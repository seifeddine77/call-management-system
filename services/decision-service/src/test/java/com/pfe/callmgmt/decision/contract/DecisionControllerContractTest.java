package com.pfe.callmgmt.decision.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.decision.controller.DecisionController;
import com.pfe.callmgmt.decision.dto.DecisionRequest;
import com.pfe.callmgmt.decision.dto.DecisionResult;
import com.pfe.callmgmt.decision.dto.OverrideRequest;
import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.service.DecisionServiceImpl;
import com.pfe.callmgmt.decision.service.LetterGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests Contract — vérifient le contrat REST du DecisionController :
 * - Formats JSON request/response
 * - Codes HTTP corrects
 * - Validation des champs obligatoires
 * - Headers (Content-Type, Content-Disposition pour PDF)
 */
@WebMvcTest(DecisionController.class)
class DecisionControllerContractTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private DecisionServiceImpl decisionService;

        @MockBean
        private LetterGenerationService letterService;

        // ──────────────────────────────────────
        // POST /api/decisions/analyze
        // ──────────────────────────────────────

        @Nested
        @DisplayName("POST /api/decisions/analyze")
        class AnalyzeEndpoint {

                @Test
                @DisplayName("200 OK — requête valide → retourne DecisionResult JSON")
                void analyze_validRequest_returns200() throws Exception {
                        UUID ticketId = UUID.randomUUID();
                        DecisionResult result = DecisionResult.builder()
                                        .decisionId(UUID.randomUUID())
                                        .ticketId(ticketId)
                                        .decision("FAVORABLE")
                                        .score(85.0)
                                        .motif("Pièce sous garantie")
                                        .source("RULES")
                                        .rulesApplied(List.of("Règle 1"))
                                        .ambiguous(false)
                                        .cached(false)
                                        .build();
                        when(decisionService.analyze(any(DecisionRequest.class))).thenReturn(result);

                        DecisionRequest request = new DecisionRequest();
                        request.setTicketId(ticketId);
                        request.setTypePiece("MECANIQUE");
                        request.setAgeVehiculeAns(3);
                        request.setKilometrage(50000);
                        request.setCategorieClient("PREMIUM");
                        request.setTypeSinistre("PANNE");

                        mockMvc.perform(post("/api/decisions/analyze")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.decisionId").exists())
                                        .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                                        .andExpect(jsonPath("$.decision").value("FAVORABLE"))
                                        .andExpect(jsonPath("$.score").value(85.0))
                                        .andExpect(jsonPath("$.motif").isString())
                                        .andExpect(jsonPath("$.source").value("RULES"))
                                        .andExpect(jsonPath("$.rulesApplied").isArray())
                                        .andExpect(jsonPath("$.rulesApplied", hasSize(1)))
                                        .andExpect(jsonPath("$.ambiguous").value(false))
                                        .andExpect(jsonPath("$.cached").value(false));
                }

                @Test
                @DisplayName("400 Bad Request — ticketId manquant")
                void analyze_missingTicketId_returns400() throws Exception {
                        String body = """
                                        {
                                            "typePiece": "MECANIQUE",
                                            "ageVehiculeAns": 3,
                                            "kilometrage": 50000,
                                            "categorieClient": "PREMIUM",
                                            "typeSinistre": "PANNE"
                                        }
                                        """;

                        mockMvc.perform(post("/api/decisions/analyze")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("400 Bad Request — typePiece vide")
                void analyze_emptyTypePiece_returns400() throws Exception {
                        String body = """
                                        {
                                            "ticketId": "550e8400-e29b-41d4-a716-446655440000",
                                            "typePiece": "",
                                            "ageVehiculeAns": 3,
                                            "kilometrage": 50000,
                                            "categorieClient": "PREMIUM",
                                            "typeSinistre": "PANNE"
                                        }
                                        """;

                        mockMvc.perform(post("/api/decisions/analyze")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("400 Bad Request — ageVehiculeAns null")
                void analyze_nullAge_returns400() throws Exception {
                        String body = """
                                        {
                                            "ticketId": "550e8400-e29b-41d4-a716-446655440000",
                                            "typePiece": "MECANIQUE",
                                            "kilometrage": 50000,
                                            "categorieClient": "PREMIUM",
                                            "typeSinistre": "PANNE"
                                        }
                                        """;

                        mockMvc.perform(post("/api/decisions/analyze")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                        .andExpect(status().isBadRequest());
                }
        }

        // ──────────────────────────────────────
        // POST /api/decisions/{id}/accept
        // ──────────────────────────────────────

        @Nested
        @DisplayName("POST /api/decisions/{id}/accept")
        class AcceptEndpoint {

                @Test
                @DisplayName("200 OK — accept valide → retourne DecisionResult avec finalDecision")
                void accept_validRequest_returns200() throws Exception {
                        UUID decisionId = UUID.randomUUID();
                        DecisionResult result = DecisionResult.builder()
                                        .decisionId(decisionId)
                                        .ticketId(UUID.randomUUID())
                                        .decision("FAVORABLE")
                                        .score(85.0)
                                        .motif("Test")
                                        .source("RULES")
                                        .finalDecision("FAVORABLE")
                                        .overrideMotif("Confirmé")
                                        .build();
                        when(decisionService.acceptDecision(eq(decisionId), any())).thenReturn(result);

                        OverrideRequest req = new OverrideRequest();
                        req.setFinalDecision("FAVORABLE");
                        req.setMotif("Confirmé par l'opérateur");

                        mockMvc.perform(post("/api/decisions/" + decisionId + "/accept")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.finalDecision").value("FAVORABLE"))
                                        .andExpect(jsonPath("$.overrideMotif").value("Confirmé"));
                }

                @Test
                @DisplayName("400 Bad Request — finalDecision invalide (ni FAVORABLE ni DEFAVORABLE)")
                void accept_invalidFinalDecision_returns400() throws Exception {
                        String body = """
                                        {
                                            "finalDecision": "MAYBE",
                                            "motif": "Je sais pas"
                                        }
                                        """;

                        mockMvc.perform(post("/api/decisions/" + UUID.randomUUID() + "/accept")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("404 Not Found — décision inexistante")
                void accept_notFound_returns404() throws Exception {
                        UUID id = UUID.randomUUID();
                        when(decisionService.acceptDecision(eq(id), any()))
                                        .thenThrow(new NoSuchElementException("Décision introuvable"));

                        OverrideRequest req = new OverrideRequest();
                        req.setFinalDecision("FAVORABLE");
                        req.setMotif("OK");

                        mockMvc.perform(post("/api/decisions/" + id + "/accept")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("409 Conflict — décision déjà finalisée")
                void accept_alreadyFinalized_returns409() throws Exception {
                        UUID id = UUID.randomUUID();
                        when(decisionService.acceptDecision(eq(id), any()))
                                        .thenThrow(new IllegalStateException("Déjà finalisée"));

                        OverrideRequest req = new OverrideRequest();
                        req.setFinalDecision("FAVORABLE");
                        req.setMotif("OK");

                        mockMvc.perform(post("/api/decisions/" + id + "/accept")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isConflict());
                }
        }

        // ──────────────────────────────────────
        // POST /api/decisions/{id}/reject
        // ──────────────────────────────────────

        @Nested
        @DisplayName("POST /api/decisions/{id}/reject")
        class RejectEndpoint {

                @Test
                @DisplayName("200 OK — reject valide")
                void reject_validRequest_returns200() throws Exception {
                        UUID decisionId = UUID.randomUUID();
                        DecisionResult result = DecisionResult.builder()
                                        .decisionId(decisionId)
                                        .ticketId(UUID.randomUUID())
                                        .decision("FAVORABLE")
                                        .finalDecision("DEFAVORABLE")
                                        .overrideMotif("Usure normale")
                                        .build();
                        when(decisionService.rejectDecision(eq(decisionId), any())).thenReturn(result);

                        OverrideRequest req = new OverrideRequest();
                        req.setFinalDecision("DEFAVORABLE");
                        req.setMotif("Usure normale");

                        mockMvc.perform(post("/api/decisions/" + decisionId + "/reject")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.finalDecision").value("DEFAVORABLE"));
                }
        }

        // ──────────────────────────────────────
        // GET /api/decisions/ticket/{ticketId}
        // ──────────────────────────────────────

        @Nested
        @DisplayName("GET /api/decisions/ticket/{ticketId}")
        class GetByTicketEndpoint {

                @Test
                @DisplayName("200 OK — ticket trouvé")
                void getByTicketId_found_returns200() throws Exception {
                        UUID ticketId = UUID.randomUUID();
                        DecisionResult result = DecisionResult.builder()
                                        .decisionId(UUID.randomUUID())
                                        .ticketId(ticketId)
                                        .decision("FAVORABLE")
                                        .score(80.0)
                                        .motif("OK")
                                        .source("RULES")
                                        .build();
                        when(decisionService.getByTicketId(ticketId)).thenReturn(result);

                        mockMvc.perform(get("/api/decisions/ticket/" + ticketId))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                                        .andExpect(jsonPath("$.decision").value("FAVORABLE"));
                }

                @Test
                @DisplayName("404 Not Found — ticket inexistant")
                void getByTicketId_notFound_returns404() throws Exception {
                        UUID ticketId = UUID.randomUUID();
                        when(decisionService.getByTicketId(ticketId))
                                        .thenThrow(new NoSuchElementException("Aucune décision"));

                        mockMvc.perform(get("/api/decisions/ticket/" + ticketId))
                                        .andExpect(status().isNotFound());
                }
        }

        // ──────────────────────────────────────
        // GET /api/decisions/{id}/letter
        // ──────────────────────────────────────

        @Nested
        @DisplayName("GET /api/decisions/{id}/letter")
        class LetterEndpoint {

                @Test
                @DisplayName("200 OK — PDF généré avec Content-Type application/pdf")
                void letter_generated_returns200WithPdf() throws Exception {
                        UUID id = UUID.randomUUID();
                        Decision decision = Decision.builder()
                                        .id(id)
                                        .ticketId(UUID.randomUUID())
                                        .decision("FAVORABLE")
                                        .finalDecision("FAVORABLE")
                                        .motif("OK")
                                        .source("RULES")
                                        .build();
                        when(decisionService.findById(id)).thenReturn(Optional.of(decision));
                        when(letterService.generateLetter(any())).thenReturn(new byte[] { 1, 2, 3, 4 });

                        mockMvc.perform(get("/api/decisions/" + id + "/letter"))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                                        .andExpect(header().string("Content-Disposition",
                                                        containsString("courrier-" + id.toString().substring(0, 8))));
                }

                @Test
                @DisplayName("400 Bad Request — décision ETUDE (pas de courrier possible)")
                void letter_etude_returns400() throws Exception {
                        UUID id = UUID.randomUUID();
                        Decision decision = Decision.builder()
                                        .id(id)
                                        .ticketId(UUID.randomUUID())
                                        .decision("ETUDE")
                                        .motif("Ambigu")
                                        .source("RULES")
                                        .build();
                        when(decisionService.findById(id)).thenReturn(Optional.of(decision));

                        mockMvc.perform(get("/api/decisions/" + id + "/letter"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("404 Not Found — décision inexistante")
                void letter_notFound_returns404() throws Exception {
                        UUID id = UUID.randomUUID();
                        when(decisionService.findById(id)).thenReturn(Optional.empty());

                        mockMvc.perform(get("/api/decisions/" + id + "/letter"))
                                        .andExpect(status().isNotFound());
                }
        }

        // ──────────────────────────────────────
        // GET /api/decisions/health
        // ──────────────────────────────────────

        @Test
        @DisplayName("GET /health — retourne 200 OK")
        void health_returns200() throws Exception {
                mockMvc.perform(get("/api/decisions/health"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("OK"));
        }
}
