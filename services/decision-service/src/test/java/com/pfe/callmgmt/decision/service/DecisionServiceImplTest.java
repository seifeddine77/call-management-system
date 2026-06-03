package com.pfe.callmgmt.decision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.decision.client.RulesServiceClient;
import com.pfe.callmgmt.decision.dto.*;
import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.repository.DecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionServiceImplTest {

        // --- SonarCloud: extracted string constants ---
        private static final String FAVORABLE = "FAVORABLE";
        private static final String DEFAVORABLE = "DEFAVORABLE";
        private static final String ETUDE = "ETUDE";
        private static final String SOURCE_RULES = "RULES";

        @Mock
        private DecisionRepository decisionRepository;
        @Mock
        private RulesServiceClient rulesServiceClient;
        @Mock
        private StringRedisTemplate redisTemplate;
        @Mock
        private RabbitTemplate rabbitTemplate;
        @Mock
        private ValueOperations<String, String> valueOperations;
        @Mock
        private LetterGenerationService letterService;
        @Mock
        private CircuitBreakerRegistry circuitBreakerRegistry;
        @Mock
        private io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
        @Mock
        private RestTemplate llmRestTemplate;
        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        private DecisionServiceImpl decisionService;

        private DecisionRequest baseRequest;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "");
                ReflectionTestUtils.setField(decisionService, "openaiModel", "ai/gemma4");
                ReflectionTestUtils.setField(decisionService, "openaiTimeoutSeconds", 60);
                ReflectionTestUtils.setField(decisionService, "openaiBaseUrl", "http://localhost:12434/engines/llama.cpp/v1");
                ReflectionTestUtils.setField(decisionService, "cacheTtlSeconds", 3600);
                ReflectionTestUtils.setField(decisionService, "storagePath", "/tmp/letters");

                baseRequest = new DecisionRequest();
                baseRequest.setTicketId(UUID.randomUUID());
                baseRequest.setTypePiece("MECANIQUE");
                baseRequest.setAgeVehiculeAns(3);
                baseRequest.setKilometrage(60000);
                baseRequest.setCategorieClient("PREMIUM");
                baseRequest.setTypeSinistre("PANNE");
        }

        @Test
        @DisplayName("analyze — décision déjà existante pour ce ticket → retour immédiat")
        void analyze_existingDecision_returnsIt() {
                Decision existing = Decision.builder()
                                .id(UUID.randomUUID())
                                .ticketId(baseRequest.getTicketId())
                                .decision(FAVORABLE)
                                .score(BigDecimal.valueOf(85.0))
                                .motif("Test")
                                .source(SOURCE_RULES)
                                .rulesApplied(List.of("Règle 1"))
                                .build();
                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.of(existing));

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(FAVORABLE);
                assertThat(result.getDecisionId()).isEqualTo(existing.getId());
                verify(rulesServiceClient, never()).evaluate(any());
        }

        @Test
        @DisplayName("analyze — cache Redis HIT → nouvelle Decision en BDD avec cached=true")
        void analyze_cacheHit_returnsCachedResult() throws Exception {
                // Pre-compute JSON BEFORE mock stubbing to avoid spy interference
                DecisionResult cached = DecisionResult.builder()
                                .decision(FAVORABLE)
                                .score(90.0)
                                .motif("Cached response")
                                .source(SOURCE_RULES)
                                .rulesApplied(List.of("R1"))
                                .build();
                String cachedJson = new ObjectMapper().writeValueAsString(cached);

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(cachedJson);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID())
                                .ticketId(baseRequest.getTicketId())
                                .decision(FAVORABLE)
                                .score(BigDecimal.valueOf(90.0))
                                .motif("Cached response")
                                .source(SOURCE_RULES)
                                .rulesApplied(List.of("R1"))
                                .build();
                when(decisionRepository.save(any(Decision.class))).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.isCached()).isTrue();
                assertThat(result.getDecision()).isEqualTo(FAVORABLE);
                verify(rulesServiceClient, never()).evaluate(any());
        }

    @Test
    @DisplayName("analyze — rules FAVORABLE, score >= 60, non ambigu → retour RULES")
    void analyze_rulesFavorable_returnsRulesDecision() {
        when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                .decision(FAVORABLE)
                .score(85.0)
                .motif("Véhicule récent, client premium")
                .rulesApplied(List.of("Règle garantie standard"))
                .ambiguous(false)
                .matchedRules(1)
                .build();
        when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

        Decision saved = Decision.builder()
                .id(UUID.randomUUID())
                .ticketId(baseRequest.getTicketId())
                .decision(FAVORABLE)
                .score(BigDecimal.valueOf(85.0))
                .motif("Véhicule récent, client premium")
                .source(SOURCE_RULES)
                .rulesApplied(List.of("Règle garantie standard"))
                .build();
        when(decisionRepository.save(any(Decision.class))).thenReturn(saved);

        DecisionResult result = decisionService.analyze(baseRequest);

        assertThat(result.getDecision()).isEqualTo(FAVORABLE);
        assertThat(result.getSource()).isEqualTo(SOURCE_RULES);
        assertThat(result.isCached()).isFalse();
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("analyze — rules DEFAVORABLE, score < 60 → retour DEFAVORABLE")
    void analyze_rulesDefavorable_returnsDefavorable() {
        when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                .decision(DEFAVORABLE)
                .score(35.0)
                .motif("Kilométrage trop élevé")
                .rulesApplied(List.of("Règle kilométrage"))
                .ambiguous(false)
                .matchedRules(1)
                .build();
        when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

        Decision saved = Decision.builder()
                .id(UUID.randomUUID())
                .ticketId(baseRequest.getTicketId())
                .decision(DEFAVORABLE)
                .score(BigDecimal.valueOf(35.0))
                .motif("Kilométrage trop élevé")
                .source(SOURCE_RULES)
                .build();
        when(decisionRepository.save(any(Decision.class))).thenReturn(saved);

        DecisionResult result = decisionService.analyze(baseRequest);

        assertThat(result.getDecision()).isEqualTo(DEFAVORABLE);
    }

    @Test
    @DisplayName("analyze — rules ambigu, pas de clé OpenAI → ETUDE fallback")
    void analyze_rulesAmbiguous_noApiKey_returnsEtude() {
        when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                .decision(ETUDE)
                .score(45.0)
                .motif("Conflit entre règles")
                .rulesApplied(List.of("R1", "R2"))
                .ambiguous(true)
                .matchedRules(2)
                .build();
        when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

        Decision saved = Decision.builder()
                .id(UUID.randomUUID())
                .ticketId(baseRequest.getTicketId())
                .decision(ETUDE)
                .score(BigDecimal.valueOf(45.0))
                .motif("Conflit entre règles")
                .source(SOURCE_RULES)
                .build();
        when(decisionRepository.save(any(Decision.class))).thenReturn(saved);

        DecisionResult result = decisionService.analyze(baseRequest);

        assertThat(result.getDecision()).isEqualTo(ETUDE);
        assertThat(result.isAmbiguous()).isTrue();
    }

    @Test
    @DisplayName("analyze — rules-service DOWN → fallback ETUDE")
    void analyze_rulesServiceDown_fallbackEtude() {
        when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(rulesServiceClient.evaluate(any())).thenThrow(new RuntimeException("Connection refused"));

        Decision saved = Decision.builder()
                .id(UUID.randomUUID())
                .ticketId(baseRequest.getTicketId())
                .decision(ETUDE)
                .score(BigDecimal.ZERO)
                .motif("Impossible de joindre le moteur de règles — analyse manuelle requise")
                .source(SOURCE_RULES)
                .build();
        when(decisionRepository.save(any(Decision.class))).thenReturn(saved);

        DecisionResult result = decisionService.analyze(baseRequest);

        assertThat(result.getDecision()).isEqualTo(ETUDE);
        assertThat(result.isAmbiguous()).isTrue();
    }

        @Test
        @DisplayName("acceptDecision — succès → finalDecision=FAVORABLE + event RabbitMQ")
        void acceptDecision_success() {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(decisionId)
                                .ticketId(UUID.randomUUID())
                                .decision(FAVORABLE)
                                .score(BigDecimal.valueOf(85.0))
                                .motif("Test")
                                .source(SOURCE_RULES)
                                .build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));
                when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(FAVORABLE);
                req.setMotif("Confirmé par l'opérateur");
                req.setOperateurId(UUID.randomUUID());
                req.setOperateurName("Jean Dupont");

                DecisionResult result = decisionService.acceptDecision(decisionId, req);

                assertThat(result.getFinalDecision()).isEqualTo(FAVORABLE);
                verify(rabbitTemplate).convertAndSend(eq("decision.exchange"), eq("decision.finalized"), any(Object.class));
        }

        @Test
        @DisplayName("rejectDecision — succès → finalDecision=DEFAVORABLE + event")
        void rejectDecision_success() {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(decisionId)
                                .ticketId(UUID.randomUUID())
                                .decision(FAVORABLE)
                                .score(BigDecimal.valueOf(85.0))
                                .motif("Test")
                                .source(SOURCE_RULES)
                                .build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));
                when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(DEFAVORABLE);
                req.setMotif("Pièce usure normale, pas de garantie");
                req.setOperateurId(UUID.randomUUID());

                DecisionResult result = decisionService.rejectDecision(decisionId, req);

                assertThat(result.getFinalDecision()).isEqualTo(DEFAVORABLE);
        }

        @Test
        @DisplayName("acceptDecision — déjà finalisée → IllegalStateException")
        void acceptDecision_alreadyFinalized_throwsException() {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(decisionId)
                                .ticketId(UUID.randomUUID())
                                .decision(FAVORABLE)
                                .finalDecision(FAVORABLE)
                                .build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(DEFAVORABLE);
                req.setMotif("Changement d'avis");

                assertThatThrownBy(() -> decisionService.acceptDecision(decisionId, req))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("déjà été finalisée");
        }

        @Test
        @DisplayName("getByTicketId — trouvé → retour DecisionResult")
        void getByTicketId_found() {
                UUID ticketId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(UUID.randomUUID())
                                .ticketId(ticketId)
                                .decision(DEFAVORABLE)
                                .score(BigDecimal.valueOf(70.0))
                                .motif("Test")
                                .source(SOURCE_RULES)
                                .build();
                when(decisionRepository.findByTicketId(ticketId)).thenReturn(Optional.of(decision));

                DecisionResult result = decisionService.getByTicketId(ticketId);

                assertThat(result.getDecision()).isEqualTo(DEFAVORABLE);
                assertThat(result.getTicketId()).isEqualTo(ticketId);
        }

        @Test
        @DisplayName("getByTicketId — introuvable → NoSuchElementException")
        void getByTicketId_notFound() {
                UUID ticketId = UUID.randomUUID();
                when(decisionRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> decisionService.getByTicketId(ticketId))
                                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("finalizeDecision — finalDecision=ETUDE → IllegalArgumentException")
        void finalizeDecision_invalidFinalDecision_throws() {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(decisionId).ticketId(UUID.randomUUID())
                                .decision(ETUDE).score(BigDecimal.valueOf(45.0))
                                .motif("Ambigu").source(SOURCE_RULES).build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(ETUDE); // invalide
                req.setMotif("Ne peut pas être ETUDE");

                assertThatThrownBy(() -> decisionService.acceptDecision(decisionId, req))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("FAVORABLE ou DEFAVORABLE");
        }

        @Test
        @DisplayName("finalizeDecision — decision introuvable → NoSuchElementException")
        void finalizeDecision_notFound_throws() {
                UUID decisionId = UUID.randomUUID();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.empty());

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(FAVORABLE);

                assertThatThrownBy(() -> decisionService.acceptDecision(decisionId, req))
                                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("finalizeDecision — letterGeneration échoue → IllegalStateException")
        void finalizeDecision_letterFails_throws() throws Exception {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(decisionId).ticketId(UUID.randomUUID())
                                .decision(FAVORABLE).score(BigDecimal.valueOf(85.0))
                                .motif("Test").source(SOURCE_RULES).build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));
                when(decisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                doThrow(new RuntimeException("PDF generation failed"))
                                .when(letterService).generateLetter(any());

                OverrideRequest req = new OverrideRequest();
                req.setFinalDecision(FAVORABLE);
                req.setMotif("Test");
                req.setOperateurId(UUID.randomUUID());

                assertThatThrownBy(() -> decisionService.acceptDecision(decisionId, req))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("courrier PDF");
        }

        @Test
        @DisplayName("deleteByTicketId — décision existante → suppression")
        void deleteByTicketId_exists_deletes() {
                UUID ticketId = UUID.randomUUID();
                Decision decision = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(ticketId)
                                .decision(FAVORABLE).source(SOURCE_RULES).build();
                when(decisionRepository.findByTicketId(ticketId)).thenReturn(Optional.of(decision));

                decisionService.deleteByTicketId(ticketId);

                verify(decisionRepository).delete(decision);
        }

        @Test
        @DisplayName("deleteByTicketId — aucune décision → ne fait rien")
        void deleteByTicketId_notExists_noOp() {
                UUID ticketId = UUID.randomUUID();
                when(decisionRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());

                decisionService.deleteByTicketId(ticketId);

                verify(decisionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("findById — retourne l'Optional")
        void findById_returnsOptional() {
                UUID decisionId = UUID.randomUUID();
                Decision decision = Decision.builder().id(decisionId).build();
                when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));

                Optional<Decision> result = decisionService.findById(decisionId);

                assertThat(result).isPresent();
                assertThat(result.get().getId()).isEqualTo(decisionId);
        }

        @Test
        @DisplayName("analyze — rules ambigu, cache null → fallback ETUDE avec score null")
        void analyze_rulesAmbiguous_nullScore_fallback() {
                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                // Rules retourne null decision et null score
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(null).score(null).motif(null)
                                .rulesApplied(null).ambiguous(true).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(ETUDE).score(BigDecimal.ZERO)
                                .motif("Analyse manuelle requise — résultat ambigu")
                                .source(SOURCE_RULES).rulesApplied(Collections.emptyList()).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(ETUDE);
        }

        @Test
        @DisplayName("analyze — cache JSON corrompu → continue vers rules")
        void analyze_corruptedCache_continuesEvaluation() {
                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn("NOT_VALID_JSON{{{{");

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(FAVORABLE).score(80.0).motif("OK")
                                .rulesApplied(List.of("R1")).ambiguous(false).matchedRules(1).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(FAVORABLE).score(BigDecimal.valueOf(80.0))
                                .motif("OK").source(SOURCE_RULES).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(FAVORABLE);
        }

        @Test
        @DisplayName("analyze — LLM succès -> retour decision LLM (source HYBRID)")
        void analyze_llmSuccess_returnsLlmDecision() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(1).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                String llmResponseBody = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\": \\\"FAVORABLE\\\", \\\"score\\\": 85.0, \\\"motif\\\": \\\"Accepté par LLM\\\"}\"}}]}";
                ResponseEntity<String> responseEntity = ResponseEntity.ok(llmResponseBody);
                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenReturn(responseEntity);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(FAVORABLE).score(BigDecimal.valueOf(85.0))
                                .motif("Accepté par LLM").source("HYBRID").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(FAVORABLE);
                assertThat(result.getSource()).isEqualTo("HYBRID");
                assertThat(result.getScore()).isEqualTo(85.0);
                assertThat(result.getMotif()).isEqualTo("Accepté par LLM");
        }

        @Test
        @DisplayName("analyze — LLM avec markdown fence -> extrait et retourne la decision")
        void analyze_llmFenceSuccess_returnsLlmDecision() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                String llmResponseBody = "{\"choices\":[{\"message\":{\"content\":\"Voici le résultat :\\n```json\\n{\\n  \\\"decision\\\": \\\"DEFAVORABLE\\\",\\n  \\\"score\\\": 35.0,\\n  \\\"motif\\\": \\\"Refusé\\\"\\n}\\n```\"}}]}";
                ResponseEntity<String> responseEntity = ResponseEntity.ok(llmResponseBody);
                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenReturn(responseEntity);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(DEFAVORABLE).score(BigDecimal.valueOf(35.0))
                                .motif("Refusé").source("AI").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(DEFAVORABLE);
                assertThat(result.getSource()).isEqualTo("AI");
                assertThat(result.getScore()).isEqualTo(35.0);
        }

        @Test
        @DisplayName("analyze — LLM avec reasoning_content uniquement -> extrait et retourne la decision")
        void analyze_llmReasoningSuccess_returnsLlmDecision() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                String llmResponseBody = "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"Raisonnement... {\\\"decision\\\": \\\"FAVORABLE\\\", \\\"score\\\": 90.0, \\\"motif\\\": \\\"Accepté\\\"}\"}}]}";
                ResponseEntity<String> responseEntity = ResponseEntity.ok(llmResponseBody);
                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenReturn(responseEntity);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(FAVORABLE).score(BigDecimal.valueOf(90.0))
                                .motif("Accepté").source("AI").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(FAVORABLE);
                assertThat(result.getSource()).isEqualTo("AI");
        }

        @Test
        @DisplayName("analyze — LLM repond sans JSON valide -> fallback ETUDE")
        void analyze_llmInvalidJson_returnsEtudeFallback() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                String llmResponseBody = "{\"choices\":[{\"message\":{\"content\":\"Désolé, pas de JSON\"}}]}";
                ResponseEntity<String> responseEntity = ResponseEntity.ok(llmResponseBody);
                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenReturn(responseEntity);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(ETUDE).score(BigDecimal.ZERO)
                                .motif("Réponse IA sans JSON exploitable — analyse manuelle requise").source("AI").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(ETUDE);
        }

        @Test
        @DisplayName("analyze — LLM retourne decision inattendue -> fallback ETUDE par normalisation")
        void analyze_llmUnexpectedDecision_defaultsToEtude() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                String llmResponseBody = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\": \\\"INCONNUE\\\", \\\"score\\\": 50.0, \\\"motif\\\": \\\"Inconnu\\\"}\"}}]}";
                ResponseEntity<String> responseEntity = ResponseEntity.ok(llmResponseBody);
                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenReturn(responseEntity);

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(ETUDE).score(BigDecimal.valueOf(50.0))
                                .motif("Inconnu").source("AI").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(ETUDE);
        }

        @Test
        @DisplayName("analyze — RestTemplate lance une exception -> fallback ETUDE")
        void analyze_llmApiError_returnsEtudeFallback() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenAnswer(inv -> {
                        java.util.function.Supplier<?> supplier = inv.getArgument(0);
                        return supplier.get();
                });

                when(llmRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                                .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(ETUDE).score(BigDecimal.ZERO)
                                .motif("Erreur d'analyse IA — réponse invalide").source("AI").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(ETUDE);
        }

        @Test
        @DisplayName("analyze — Circuit Breaker ouvert -> fallback ETUDE (source CIRCUIT_BREAKER)")
        void analyze_circuitBreakerOpen_returnsCircuitBreakerFallback() {
                ReflectionTestUtils.setField(decisionService, "openaiApiKey", "test-api-key");

                when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);

                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(ETUDE).score(45.0).motif("Ambigu")
                                .rulesApplied(List.of("R1")).ambiguous(true).matchedRules(0).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                when(circuitBreakerRegistry.circuitBreaker("llm")).thenReturn(circuitBreaker);
                when(circuitBreaker.executeSupplier(any())).thenThrow(new RuntimeException("Call not permitted by circuit breaker"));

                Decision saved = Decision.builder()
                                .id(UUID.randomUUID()).ticketId(baseRequest.getTicketId())
                                .decision(ETUDE).score(BigDecimal.ZERO)
                                .motif("Service IA temporairement indisponible — analyse manuelle requise").source("CIRCUIT_BREAKER").rulesApplied(List.of("R1")).build();
                when(decisionRepository.save(any())).thenReturn(saved);

                DecisionResult result = decisionService.analyze(baseRequest);

                assertThat(result.getDecision()).isEqualTo(ETUDE);
                assertThat(result.getSource()).isEqualTo("CIRCUIT_BREAKER");
        }
}

