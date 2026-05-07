package com.pfe.callmgmt.decision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.decision.client.RulesServiceClient;
import com.pfe.callmgmt.decision.config.RabbitMQConfig;
import com.pfe.callmgmt.decision.dto.*;
import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.event.DecisionFinalizedEvent;
import com.pfe.callmgmt.decision.repository.DecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionServiceImpl {

    // SonarCloud HIGH fix — extract duplicated decision literals into constants
    private static final String DECISION_ETUDE = "ETUDE";
    private static final String DECISION_FAVORABLE = "FAVORABLE";
    private static final String DECISION_DEFAVORABLE = "DEFAVORABLE";
    private static final String KEY_CONTENT = "content";

    // SonarCloud C2 fix — Pattern compiled once as static final (S5247)
    private static final java.util.regex.Pattern FENCE_PATTERN = java.util.regex.Pattern.compile(
            "```(?:json)?\\s*\\n?(\\{[^`]*?\\})\\s*\\n?```", java.util.regex.Pattern.DOTALL);

    private final DecisionRepository decisionRepository;
    private final RulesServiceClient rulesServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate llmRestTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${openai.timeout-seconds:30}")
    private int openaiTimeoutSeconds;

    @Value("${openai.base-url:http://localhost:11434/v1}")
    private String openaiBaseUrl;

    @Value("${decision.cache.ttl-seconds:3600}")
    private int cacheTtlSeconds;

    // ──────────────────────────────────────────────
    // ANALYSE — Flux 3 étapes (CDC Module 5)
    // ──────────────────────────────────────────────

    @Transactional
    public DecisionResult analyze(DecisionRequest request) {
        // Check if decision already exists for this ticket
        Optional<Decision> existing = decisionRepository.findByTicketId(request.getTicketId());
        if (existing.isPresent()) {
            return toResult(existing.get(), false);
        }

        // ── STEP 1: Redis Cache ──
        String cacheKey = buildCacheKey(request);
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                DecisionResult cached = objectMapper.readValue(cachedJson, DecisionResult.class);
                // Save with new ticketId (cache is parameter-based, not ticket-based)
                Decision decision = saveToDB(request.getTicketId(), cached.getDecision(),
                        BigDecimal.valueOf(cached.getScore()), cached.getMotif(),
                        cached.getSource(), cached.getRulesApplied());
                cached.setDecisionId(decision.getId());
                cached.setTicketId(request.getTicketId());
                cached.setCached(true);
                log.info("✅ Cache HIT for ticket {} — decision: {}", request.getTicketId(), cached.getDecision());
                return cached;
            } catch (JsonProcessingException e) {
                log.warn("Cache deserialization failed, continuing to rules evaluation", e);
            }
        }

        // ── STEP 2: Rules Engine (via Feign) ──
        RulesEvaluateResponse rulesResult;
        try {
            RulesEvaluateRequest rulesRequest = RulesEvaluateRequest.builder()
                    .typePiece(request.getTypePiece())
                    .ageVehiculeAns(request.getAgeVehiculeAns())
                    .kilometrage(request.getKilometrage())
                    .categorieClient(request.getCategorieClient())
                    .typeSinistre(request.getTypeSinistre())
                    .pieceReference(request.getPieceReference())
                    .transcriptionText(request.getTranscriptionText())
                    // Duplicate into data map for dynamic field evaluation
                    .data(java.util.Map.of(
                            "typePiece", request.getTypePiece() != null ? request.getTypePiece() : "",
                            "ageVehiculeAns", request.getAgeVehiculeAns() != null ? request.getAgeVehiculeAns() : 0,
                            "kilometrage", request.getKilometrage() != null ? request.getKilometrage() : 0,
                            "categorieClient", request.getCategorieClient() != null ? request.getCategorieClient() : "",
                            "typeSinistre", request.getTypeSinistre() != null ? request.getTypeSinistre() : "",
                            "pieceReference", request.getPieceReference() != null ? request.getPieceReference() : "",
                            "transcriptionText", request.getTranscriptionText() != null ? request.getTranscriptionText() : ""
                    ))
                    .build();

            rulesResult = rulesServiceClient.evaluate(rulesRequest);
            log.info("📏 Rules Engine result for ticket {}: decision={}, score={}, ambiguous={}",
                    request.getTicketId(), rulesResult.getDecision(), rulesResult.getScore(),
                    rulesResult.getAmbiguous());
        } catch (Exception e) {
            log.error("❌ Rules Service call failed for ticket {}: {}", request.getTicketId(), e.getMessage());
            // Fallback: mark as ETUDE
            rulesResult = RulesEvaluateResponse.builder()
                    .decision(DECISION_ETUDE)
                    .score(0.0)
                    .motif("Impossible de joindre le moteur de règles — analyse manuelle requise")
                    .rulesApplied(Collections.emptyList())
                    .ambiguous(true)
                    .build();
        }

        // If rules gave a clear answer (not ambiguous, score >= 60)
        if (!Boolean.TRUE.equals(rulesResult.getAmbiguous()) && rulesResult.getScore() != null
                && rulesResult.getScore() >= 60.0) {
            Decision decision = saveToDB(request.getTicketId(), rulesResult.getDecision(),
                    BigDecimal.valueOf(rulesResult.getScore()), rulesResult.getMotif(),
                    "RULES", rulesResult.getRulesApplied());
            DecisionResult result = toResult(decision, false);
            cacheResult(cacheKey, result);
            return result;
        }

        // ── STEP 3: LLM local Gemma 4 via Ollama (cas ambigus) ──
        // Programmatic CircuitBreaker (annotation-based doesn't work on private methods)
        final RulesEvaluateResponse finalRulesResult = rulesResult;
        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("llm");
            try {
                DecisionResult aiResult = cb.executeSupplier(() -> callOpenAI(request, finalRulesResult));
                String source = (rulesResult.getMatchedRules() != null && rulesResult.getMatchedRules() > 0) ? "HYBRID"
                        : "AI";
                List<String> allRules = new ArrayList<>();
                if (rulesResult.getRulesApplied() != null)
                    allRules.addAll(rulesResult.getRulesApplied());

                Decision decision = saveToDB(request.getTicketId(), aiResult.getDecision(),
                        BigDecimal.valueOf(aiResult.getScore()), aiResult.getMotif(),
                        source, allRules);
                DecisionResult result = toResult(decision, false);
                cacheResult(cacheKey, result);
                return result;
            } catch (Exception e) {
                log.warn("⚡ Circuit breaker LLM — fallback ETUDE pour ticket {}. Cause: {}",
                        request.getTicketId(), e.getMessage());
                // Save with CIRCUIT_BREAKER source when LLM is unavailable
                Decision decision = saveToDB(request.getTicketId(), DECISION_ETUDE,
                        BigDecimal.ZERO,
                        "Service IA temporairement indisponible — analyse manuelle requise",
                        "CIRCUIT_BREAKER",
                        rulesResult.getRulesApplied() != null ? rulesResult.getRulesApplied() : Collections.emptyList());
                return toResult(decision, false);
            }
        } else {
            log.info("ℹ️ No LLM API key configured — skipping AI step");
        }

        // Fallback: use rules result as-is (ETUDE if ambiguous)
        Decision decision = saveToDB(request.getTicketId(),
                rulesResult.getDecision() != null ? rulesResult.getDecision() : DECISION_ETUDE,
                BigDecimal.valueOf(rulesResult.getScore() != null ? rulesResult.getScore() : 0.0),
                rulesResult.getMotif() != null ? rulesResult.getMotif() : "Analyse manuelle requise — résultat ambigu",
                "RULES",
                rulesResult.getRulesApplied() != null ? rulesResult.getRulesApplied() : Collections.emptyList());
        return toResult(decision, false);
    }

    // ──────────────────────────────────────────────
    // ACCEPT / REJECT
    // ──────────────────────────────────────────────

    @Transactional
    public DecisionResult acceptDecision(UUID decisionId, OverrideRequest request) {
        return finalizeDecision(decisionId, request);
    }

    @Transactional
    public DecisionResult rejectDecision(UUID decisionId, OverrideRequest request) {
        return finalizeDecision(decisionId, request);
    }

    private DecisionResult finalizeDecision(UUID decisionId, OverrideRequest request) {
        Decision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new NoSuchElementException("Décision introuvable : " + decisionId));

        if (decision.getFinalDecision() != null) {
            throw new IllegalStateException(
                    "Cette décision a déjà été finalisée (" + decision.getFinalDecision() + ")");
        }

        // CDC 5.D — ETUDE n'est jamais une décision finale
        String fd = request.getFinalDecision();
        if (!DECISION_FAVORABLE.equals(fd) && !DECISION_DEFAVORABLE.equals(fd)) {
            throw new IllegalArgumentException(
                    "La décision finale doit être FAVORABLE ou DEFAVORABLE (CDC 5.D). Reçu : " + fd);
        }

        decision.setFinalDecision(request.getFinalDecision());
        decision.setOverrideMotif(request.getMotif());
        decision.setOverrideBy(request.getOperateurId());
        decision.setOverrideByName(request.getOperateurName());
        decision.setOverriddenAt(LocalDateTime.now());
        decisionRepository.save(decision);

        // Publish event for notification-service
        DecisionFinalizedEvent event = DecisionFinalizedEvent.builder()
                .decisionId(decision.getId())
                .ticketId(decision.getTicketId())
                .clientNom(request.getClientNom())
                .clientEmail(request.getClientEmail())
                .finalDecision(request.getFinalDecision())
                .motif(decision.getMotif())
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConfig.DECISION_EXCHANGE, RabbitMQConfig.DECISION_FINALIZED_ROUTING_KEY, event);
        log.info("📤 Published decision.finalized event for ticket {}: {}", decision.getTicketId(),
                request.getFinalDecision());

        return toResult(decision, false);
    }

    // ──────────────────────────────────────────────
    // GET BY TICKET
    // ──────────────────────────────────────────────

    public DecisionResult getByTicketId(UUID ticketId) {
        Decision decision = decisionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Aucune décision pour le ticket : " + ticketId));
        return toResult(decision, false);
    }

    @Transactional
    public void deleteByTicketId(UUID ticketId) {
        decisionRepository.findByTicketId(ticketId).ifPresent(decision -> {
            decisionRepository.delete(decision);
            log.info("🗑️ Décision {} supprimée pour ticket {} — ré-analyse possible",
                    decision.getId(), ticketId);
        });
    }

    public Optional<Decision> findById(UUID decisionId) {
        return decisionRepository.findById(decisionId);
    }

    // ──────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────

    private Decision saveToDB(UUID ticketId, String decisionStr, BigDecimal score,
            String motif, String source, List<String> rulesApplied) {
        Decision decision = Decision.builder()
                .ticketId(ticketId)
                .decision(decisionStr)
                .score(score)
                .motif(motif)
                .source(source)
                .rulesApplied(rulesApplied)
                .build();
        return decisionRepository.save(decision);
    }

    private DecisionResult toResult(Decision d, boolean cached) {
        return DecisionResult.builder()
                .decisionId(d.getId())
                .ticketId(d.getTicketId())
                .decision(d.getDecision())
                .score(d.getScore() != null ? d.getScore().doubleValue() : 0.0)
                .motif(d.getMotif())
                .source(d.getSource())
                .rulesApplied(d.getRulesApplied() != null ? d.getRulesApplied() : Collections.emptyList())
                .ambiguous(DECISION_ETUDE.equals(d.getDecision()))
                .cached(cached)
                .finalDecision(d.getFinalDecision())
                .overrideMotif(d.getOverrideMotif())
                .build();
    }

    private String buildCacheKey(DecisionRequest req) {
        String raw = req.getTypePiece() + "|" + req.getAgeVehiculeAns() + "|" +
                req.getKilometrage() + "|" + req.getCategorieClient() + "|" + req.getTypeSinistre();
        try {
            // SonarCloud B1 fix — SHA-256 instead of MD5 (S4790 security hotspot)
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return "decision:" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "decision:" + raw.hashCode();
        }
    }

    private void cacheResult(String key, DecisionResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(cacheTtlSeconds));
            log.debug("Cached decision result with key: {}", key);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache decision result", e);
        }
    }

    private DecisionResult callOpenAI(DecisionRequest request, RulesEvaluateResponse rulesResult) {
        // ── Prompt engineering for Gemma 4 (thinking model) ──
        // Gemma 4 has an internal "thinking" phase. We must be very explicit
        // about the output format. The system prompt is kept minimal and
        // directive to maximise the chance the model outputs clean JSON.
        String systemPrompt = "Tu es un expert assurance automobile. " +
                "Reponds UNIQUEMENT avec un objet JSON valide, sans aucun texte avant ou apres. " +
                "Le JSON doit contenir exactement ces 3 champs: " +
                "\"decision\" (valeur: \"FAVORABLE\" ou \"DEFAVORABLE\" ou \"ETUDE\"), " +
                "\"score\" (nombre entier 0 a 100), " +
                "\"motif\" (courte phrase en francais expliquant la decision). " +
                "Ne mets PAS de markdown, pas de commentaire, pas de texte supplementaire. " +
                "Reponds UNIQUEMENT avec le JSON.";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Analyse cette demande d'assurance automobile:\n");
        userPrompt.append("- Type de piece: ").append(request.getTypePiece()).append("\n");
        userPrompt.append("- Age du vehicule: ").append(request.getAgeVehiculeAns()).append(" ans\n");
        userPrompt.append("- Kilometrage: ").append(request.getKilometrage()).append(" km\n");
        userPrompt.append("- Type de sinistre: ").append(request.getTypeSinistre()).append("\n");
        userPrompt.append("- Categorie client: ").append(request.getCategorieClient()).append("\n");
        if (request.getTranscriptionText() != null && !request.getTranscriptionText().isBlank()) {
            String summary = request.getTranscriptionText().substring(0,
                    Math.min(200, request.getTranscriptionText().length()));
            userPrompt.append("- Resume de l'appel: ").append(summary).append("\n");
        }

        // Build request body — llama.cpp supports response_format for structured output
        Map<String, Object> body = new HashMap<>();
        body.put("model", openaiModel);
        body.put("max_tokens", 1024);   // Thinking models need headroom for reasoning + answer
        body.put("temperature", 0.1);   // Near-deterministic but avoids degenerate repeats at 0
        body.put("response_format", Map.of("type", "json_object")); // Force JSON output
        body.put("messages", List.of(
                Map.of("role", "system", KEY_CONTENT, systemPrompt),
                Map.of("role", "user", KEY_CONTENT, userPrompt.toString())));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openaiApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // SonarCloud B2 fix — use injected RestTemplate (no resource leak)
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);
        log.info("🚀 Calling Gemma 4 LLM for ticket {} ...", request.getTicketId());

        ResponseEntity<String> response = llmRestTemplate.exchange(
                openaiBaseUrl + "/chat/completions",
                HttpMethod.POST, httpEntity, String.class);

        // ── Parse response — robust extraction for thinking models ──
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode messageNode = root.path("choices").get(0).path("message");

            // Strategy: try "content" first, then "reasoning_content"
            // Gemma 4 in thinking mode may put the JSON answer in "content"
            // (especially with response_format=json_object) or embed it
            // within "reasoning_content" if the model runs out of tokens
            // for the content field.
            String content = messageNode.path(KEY_CONTENT).asText("");
            String reasoning = messageNode.path("reasoning_content").asText("");

            log.debug("🔍 LLM raw content ({} chars): {}", content.length(),
                    content.substring(0, Math.min(300, content.length())));
            if (!reasoning.isBlank()) {
                log.debug("🔍 LLM raw reasoning ({} chars): {}...", reasoning.length(),
                        reasoning.substring(0, Math.min(200, reasoning.length())));
            }

            // Choose the best source for JSON extraction
            String textToParse = content;
            if (content.isBlank() && !reasoning.isBlank()) {
                log.info("📝 Gemma 4 thinking mode — content empty, using reasoning_content ({} chars)", reasoning.length());
                textToParse = reasoning;
            }

            // ── Robust JSON extraction via regex ──
            // Handles: raw JSON, ```json ... ```, text before/after JSON,
            // and multiple JSON objects in thinking chain (picks the last one
            // which is typically the final answer).
            String jsonStr = extractJsonObject(textToParse);

            if (jsonStr == null) {
                log.error("❌ No valid JSON object found in LLM response. Full text: {}", textToParse);
                return DecisionResult.builder()
                        .decision(DECISION_ETUDE).score(0.0)
                        .motif("Réponse IA sans JSON exploitable — analyse manuelle requise")
                        .build();
            }

            JsonNode aiResponse = objectMapper.readTree(jsonStr);

            String aiDecision = aiResponse.path("decision").asText(DECISION_ETUDE).toUpperCase().trim();
            // Normalize decision values
            if (!List.of(DECISION_FAVORABLE, DECISION_DEFAVORABLE, DECISION_ETUDE).contains(aiDecision)) {
                log.warn("⚠️ LLM returned unexpected decision '{}', defaulting to ETUDE", aiDecision);
                aiDecision = DECISION_ETUDE;
            }
            double aiScore = aiResponse.path("score").asDouble(50.0);
            String aiMotif = aiResponse.path("motif").asText("Analyse IA locale");

            log.info("🤖 Gemma 4 decision: {} | score: {} | motif: {}", aiDecision, aiScore, aiMotif);

            return DecisionResult.builder()
                    .decision(aiDecision)
                    .score(aiScore)
                    .motif(aiMotif)
                    .build();
        } catch (Exception e) {
            log.error("❌ Erreur parsing réponse LLM: {} | Body: {}", e.getMessage(),
                    response.getBody() != null ? response.getBody().substring(0, Math.min(500, response.getBody().length())) : "null");
            return DecisionResult.builder()
                    .decision(DECISION_ETUDE)
                    .score(0.0)
                    .motif("Erreur d'analyse IA — réponse invalide")
                    .build();
        }
    }

    /**
     * Extracts the last valid JSON object from a text that may contain
     * free-form reasoning, markdown fences, or multiple JSON snippets.
     * Thinking models like Gemma 4 often embed the final answer JSON
     * at the end of their reasoning chain.
     */
    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) return null;

        // Step 1: Try to extract from markdown ```json ... ``` fence
        // SonarCloud C2 fix — using static final FENCE_PATTERN instead of compiling inline
        java.util.regex.Matcher fenceMatcher = FENCE_PATTERN.matcher(text);
        String lastFenced = null;
        while (fenceMatcher.find()) {
            lastFenced = fenceMatcher.group(1).trim();
        }
        if (lastFenced != null) {
            log.debug("📎 Extracted JSON from markdown fence");
            return lastFenced;
        }

        // Step 2: Find all balanced { } blocks and return the last one
        // that successfully parses as JSON with our expected fields
        int depth = 0;
        int blockStart = -1;
        String bestCandidate = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) blockStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    String candidate = text.substring(blockStart, i + 1);
                    // Validate: must contain at least "decision" field
                    if (candidate.contains("\"decision\"") || candidate.contains("decision")) {
                        bestCandidate = candidate;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            log.debug("📎 Extracted JSON via balanced-brace scan ({} chars)", bestCandidate.length());
        }
        return bestCandidate;
    }

    // Circuit Breaker fallback is now handled programmatically in analyze() method
    // using CircuitBreakerRegistry.circuitBreaker("llm").executeSupplier() — see Step 3
}
