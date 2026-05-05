package com.pfe.callmgmt.decision.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.decision.client.RulesServiceClient;
import com.pfe.callmgmt.decision.dto.*;
import com.pfe.callmgmt.decision.repository.DecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration — Spring Boot context réel + MockMvc.
 * Teste le flux complet : Controller → Service → (Mocked) Dependencies.
 * Redis + RabbitMQ + Feign sont mockés, mais le wiring Spring est réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionIntegrationTest {

        // --- SonarCloud: extracted string constants ---
        private static final String API_DECISIONS_ANALYZE = "/api/decisions/analyze";
        private static final String API_DECISIONS = "/api/decisions/";
        private static final String JSON_DECISION = "$.decision";
        private static final String FAVORABLE = "FAVORABLE";
        private static final String DEFAVORABLE = "DEFAVORABLE";
        private static final String STANDARD = "STANDARD";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private DecisionRepository decisionRepository;

        @MockBean
        private RulesServiceClient rulesServiceClient;

        @MockBean
        private StringRedisTemplate redisTemplate;

        @MockBean
        private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

        @MockBean
        private ValueOperations<String, String> valueOperations;

        @BeforeEach
        void setUp() {
                decisionRepository.deleteAll();
                when(redisTemplate.opsForValue()).thenReturn(valueOperations);
                when(valueOperations.get(anyString())).thenReturn(null);
        }

        @Test
        @DisplayName("Flux complet : analyze → accept → letter (intégration)")
        void fullFlow_analyze_accept_letter() throws Exception {
                // 1. Mock rules-service response
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(FAVORABLE)
                                .score(85.0)
                                .motif("Pièce mécanique sous garantie")
                                .rulesApplied(List.of("Règle garantie"))
                                .ambiguous(false)
                                .matchedRules(1)
                                .build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                UUID ticketId = UUID.randomUUID();

                // 2. POST /analyze
                DecisionRequest request = new DecisionRequest();
                request.setTicketId(ticketId);
                request.setTypePiece("MECANIQUE");
                request.setAgeVehiculeAns(2);
                request.setKilometrage(30000);
                request.setCategorieClient("PREMIUM");
                request.setTypeSinistre("PANNE");

                MvcResult analyzeResult = mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath(JSON_DECISION).value(FAVORABLE))
                                .andExpect(jsonPath("$.score").value(85.0))
                                .andExpect(jsonPath("$.source").value("RULES"))
                                .andReturn();

                String responseJson = analyzeResult.getResponse().getContentAsString();
                DecisionResult decisionResult = objectMapper.readValue(responseJson, DecisionResult.class);
                UUID decisionId = decisionResult.getDecisionId();

                // Vérifier persistance en BDD
                assertThat(decisionRepository.findByTicketId(ticketId)).isPresent();

                // 3. POST /accept
                OverrideRequest acceptReq = new OverrideRequest();
                acceptReq.setFinalDecision(FAVORABLE);
                acceptReq.setMotif("Confirmé par opérateur");

                mockMvc.perform(post(API_DECISIONS + decisionId + "/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(acceptReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.finalDecision").value(FAVORABLE))
                                .andExpect(jsonPath("$.overrideMotif").value("Confirmé par opérateur"));

                // 4. GET /letter (PDF)
                mockMvc.perform(get(API_DECISIONS + decisionId + "/letter"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        }

        @Test
        @DisplayName("Flux complet : analyze → reject → vérifier 409 sur re-reject")
        void fullFlow_analyze_reject_doubleReject() throws Exception {
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(DEFAVORABLE)
                                .score(70.0)
                                .motif("Kilométrage trop élevé")
                                .rulesApplied(List.of("Règle km"))
                                .ambiguous(false)
                                .matchedRules(1)
                                .build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                UUID ticketId = UUID.randomUUID();
                DecisionRequest request = new DecisionRequest();
                request.setTicketId(ticketId);
                request.setTypePiece("MECANIQUE");
                request.setAgeVehiculeAns(8);
                request.setKilometrage(180000);
                request.setCategorieClient(STANDARD);
                request.setTypeSinistre("USURE");

                MvcResult analyzeResult = mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath(JSON_DECISION).value(DEFAVORABLE))
                                .andReturn();

                DecisionResult result = objectMapper.readValue(
                                analyzeResult.getResponse().getContentAsString(), DecisionResult.class);

                // Reject
                OverrideRequest rejectReq = new OverrideRequest();
                rejectReq.setFinalDecision(DEFAVORABLE);
                rejectReq.setMotif("Usure normale confirmée");

                mockMvc.perform(post(API_DECISIONS + result.getDecisionId() + "/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(rejectReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.finalDecision").value(DEFAVORABLE));

                // Double reject → 409 Conflict
                mockMvc.perform(post(API_DECISIONS + result.getDecisionId() + "/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(rejectReq)))
                                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("analyze — ticket déjà traité → retour identique (idempotent)")
        void analyze_sameTicket_idempotent() throws Exception {
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(FAVORABLE).score(90.0).motif("OK")
                                .rulesApplied(List.of("R1")).ambiguous(false).matchedRules(1).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                UUID ticketId = UUID.randomUUID();
                DecisionRequest request = new DecisionRequest();
                request.setTicketId(ticketId);
                request.setTypePiece("ELECTRIQUE");
                request.setAgeVehiculeAns(1);
                request.setKilometrage(10000);
                request.setCategorieClient("VIP");
                request.setTypeSinistre("DEFAUT_FABRICATION");

                // Premier appel
                MvcResult first = mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();

                // Deuxième appel avec le même ticketId
                MvcResult second = mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();

                DecisionResult r1 = objectMapper.readValue(first.getResponse().getContentAsString(),
                                DecisionResult.class);
                DecisionResult r2 = objectMapper.readValue(second.getResponse().getContentAsString(),
                                DecisionResult.class);

                assertThat(r1.getDecisionId()).isEqualTo(r2.getDecisionId());
                // Rules service should only be called once
                verify(rulesServiceClient, times(1)).evaluate(any());
        }

        @Test
        @DisplayName("GET /ticket/{id} — ticket trouvé après analyze")
        void getByTicketId_afterAnalyze() throws Exception {
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision(FAVORABLE).score(80.0).motif("OK")
                                .rulesApplied(List.of("R1")).ambiguous(false).matchedRules(1).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                UUID ticketId = UUID.randomUUID();
                DecisionRequest request = new DecisionRequest();
                request.setTicketId(ticketId);
                request.setTypePiece("CARROSSERIE");
                request.setAgeVehiculeAns(4);
                request.setKilometrage(80000);
                request.setCategorieClient(STANDARD);
                request.setTypeSinistre("ACCIDENT");

                mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/decisions/ticket/" + ticketId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                                .andExpect(jsonPath(JSON_DECISION).value(FAVORABLE));
        }

        @Test
        @DisplayName("GET /ticket/{id} — ticket inexistant → 404")
        void getByTicketId_notFound() throws Exception {
                mockMvc.perform(get("/api/decisions/ticket/" + UUID.randomUUID()))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("letter pour ETUDE → 400 Bad Request")
        void letter_etude_returns400() throws Exception {
                // Créer une décision ETUDE
                RulesEvaluateResponse rulesResp = RulesEvaluateResponse.builder()
                                .decision("ETUDE").score(40.0).motif("Ambigu")
                                .rulesApplied(List.of("R1", "R2")).ambiguous(true).matchedRules(2).build();
                when(rulesServiceClient.evaluate(any())).thenReturn(rulesResp);

                UUID ticketId = UUID.randomUUID();
                DecisionRequest request = new DecisionRequest();
                request.setTicketId(ticketId);
                request.setTypePiece("ELECTRONIQUE");
                request.setAgeVehiculeAns(5);
                request.setKilometrage(100000);
                request.setCategorieClient(STANDARD);
                request.setTypeSinistre("PANNE");

                MvcResult result = mockMvc.perform(post(API_DECISIONS_ANALYZE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath(JSON_DECISION).value("ETUDE"))
                                .andReturn();

                DecisionResult dr = objectMapper.readValue(result.getResponse().getContentAsString(),
                                DecisionResult.class);

                // PDF pour ETUDE → 400
                mockMvc.perform(get(API_DECISIONS + dr.getDecisionId() + "/letter"))
                                .andExpect(status().isBadRequest());
        }
}
