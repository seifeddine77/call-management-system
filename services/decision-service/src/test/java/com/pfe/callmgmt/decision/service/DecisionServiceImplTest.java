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
    @DisplayName("analyze — rules DEFAVORABLE, score >= 60 → retour DEFAVORABLE")
    void analyze_rulesDefavorable_returnsDefavorable() {
        when(decisionRepository.findByTicketId(baseRequest.getTicketId())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                .decision(DEFAVORABLE)
                .score(70.0)
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
                .score(BigDecimal.valueOf(70.0))
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
}
