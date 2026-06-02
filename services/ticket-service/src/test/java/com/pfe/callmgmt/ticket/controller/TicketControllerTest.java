package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.client.OrganisationServiceClient;
import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.service.AudioEncryptionService;
import com.pfe.callmgmt.ticket.service.QueueService;
import com.pfe.callmgmt.ticket.service.SupervisionService;
import com.pfe.callmgmt.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires — TicketController.
 * Couvre les endpoints non couverts par les tests d'intégration.
 */
@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";

    @Mock private TicketService ticketService;
    @Mock private QueueService queueService;
    @Mock private SupervisionService supervisionService;
    @Mock private AiServiceClient aiServiceClient;
    @Mock private OrganisationServiceClient organisationClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private AudioEncryptionService audioEncryptionService;

    private TicketController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketController(
                ticketService, queueService, supervisionService,
                aiServiceClient, organisationClient, objectMapper, audioEncryptionService);
    }

    // ═══════════════════════════════════════
    // health
    // ═══════════════════════════════════════

    @Test
    @DisplayName("GET /health → retourne OK")
    void health_returnsOk() {
        ResponseEntity<String> response = controller.health();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("OK");
    }

    // ═══════════════════════════════════════
    // getTranscription
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("getTranscription")
    class GetTranscription {

        @Test
        @DisplayName("Ticket avec transcription → retourne les données")
        void withTranscription_returnsData() {
            UUID ticketId = UUID.randomUUID();
            TicketResponse ticket = new TicketResponse();
            ticket.setId(ticketId);
            ticket.setTranscription("Le client signale un problème de frein");
            ticket.setAudioPath("/audio/test.wav");
            ticket.setTranscriptionKeywords("[\"frein\",\"problème\"]");

            when(ticketService.getTicketById(eq(ticketId), eq(ROLE_OPERATOR), any(), any()))
                    .thenReturn(ticket);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) controller.getTranscription(
                            ticketId, ROLE_OPERATOR, UUID.randomUUID(), null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        @DisplayName("Ticket sans transcription ni audio → retourne 404")
        void noTranscription_returns404() {
            UUID ticketId = UUID.randomUUID();
            TicketResponse ticket = new TicketResponse();
            ticket.setId(ticketId);
            ticket.setTranscription(null);
            ticket.setAudioPath(null);

            when(ticketService.getTicketById(eq(ticketId), eq(ROLE_OPERATOR), any(), any()))
                    .thenReturn(ticket);

            ResponseEntity<?> response = controller.getTranscription(
                    ticketId, ROLE_OPERATOR, UUID.randomUUID(), null);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }
    }

    // ═══════════════════════════════════════
    // anonymizeTicket
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("anonymizeTicket")
    class AnonymizeTicket {

        @Test
        @DisplayName("ADMIN → anonymise le ticket avec succès")
        void admin_anonymizesSuccessfully() {
            UUID ticketId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            TicketResponse anonymized = new TicketResponse();
            anonymized.setId(ticketId);
            anonymized.setClientNom("***ANONYMISÉ***");

            when(ticketService.anonymizeTicket(ticketId, adminId, "admin@test.com"))
                    .thenReturn(anonymized);

            ResponseEntity<TicketResponse> response = controller.anonymizeTicket(
                    ticketId, adminId, "admin@test.com", ROLE_ADMIN);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getClientNom()).isEqualTo("***ANONYMISÉ***");
        }

        @Test
        @DisplayName("Non-ADMIN → retourne 403 Forbidden")
        void nonAdmin_returnsForbidden() {
            UUID ticketId = UUID.randomUUID();

            ResponseEntity<TicketResponse> response = controller.anonymizeTicket(
                    ticketId, UUID.randomUUID(), "op@test.com", ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(403);
            verifyNoInteractions(ticketService);
        }
    }

    // ═══════════════════════════════════════
    // getTicketStats
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("getTicketStats")
    class GetTicketStats {

        @Test
        @DisplayName("ADMIN avec agenceId explicite → utilise l'agence du param")
        void admin_usesExplicitAgenceId() {
            UUID agenceId = UUID.randomUUID();
            TicketStats stats = new TicketStats();
            when(ticketService.getTicketStats(agenceId)).thenReturn(stats);

            ResponseEntity<TicketStats> response = controller.getTicketStats(
                    agenceId, UUID.randomUUID(), ROLE_ADMIN);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).getTicketStats(agenceId);
        }

        @Test
        @DisplayName("OPERATOR → utilise l'agence du header (pas le param)")
        void operator_usesHeaderAgenceId() {
            UUID headerAgenceId = UUID.randomUUID();
            UUID paramAgenceId = UUID.randomUUID();
            TicketStats stats = new TicketStats();
            when(ticketService.getTicketStats(headerAgenceId)).thenReturn(stats);

            ResponseEntity<TicketStats> response = controller.getTicketStats(
                    paramAgenceId, headerAgenceId, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).getTicketStats(headerAgenceId);
        }
    }

    // ═══════════════════════════════════════
    // getQueue
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("getQueue")
    class GetQueue {

        @Test
        @DisplayName("ADMIN avec agenceId → retourne la file de l'agence spécifiée")
        void admin_usesExplicitAgenceId() {
            UUID agenceId = UUID.randomUUID();
            QueueResponse queue = new QueueResponse();
            when(queueService.getQueue(agenceId)).thenReturn(queue);

            ResponseEntity<QueueResponse> response = controller.getQueue(
                    agenceId, UUID.randomUUID(), ROLE_ADMIN);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(queueService).getQueue(agenceId);
        }

        @Test
        @DisplayName("OPERATOR → utilise l'agence du header")
        void operator_usesHeaderAgenceId() {
            UUID headerAgenceId = UUID.randomUUID();
            QueueResponse queue = new QueueResponse();
            when(queueService.getQueue(headerAgenceId)).thenReturn(queue);

            ResponseEntity<QueueResponse> response = controller.getQueue(
                    null, headerAgenceId, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(queueService).getQueue(headerAgenceId);
        }
    }

    // ═══════════════════════════════════════
    // getOperatorPerformance
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("getOperatorPerformance")
    class GetOperatorPerformance {

        @Test
        @DisplayName("Résolution des noms via Feign réussie")
        void resolveNames_success() {
            UUID opId = UUID.randomUUID();
            OperatorPerformanceResponse perf = new OperatorPerformanceResponse();
            perf.setOperateurId(opId);
            perf.setOperateurName(null); // à résoudre

            when(supervisionService.getOperatorPerformance("30d")).thenReturn(List.of(perf));
            when(organisationClient.getUserById(opId))
                    .thenReturn(Map.of("prenom", "Jean", "nom", "Dupont"));

            ResponseEntity<List<OperatorPerformanceResponse>> response =
                    controller.getOperatorPerformance("30d");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get(0).getOperateurName()).isEqualTo("Jean Dupont");
        }

        @Test
        @DisplayName("Résolution des noms échoue → fallback avec ID partiel")
        void resolveNames_feignFails_fallback() {
            UUID opId = UUID.randomUUID();
            OperatorPerformanceResponse perf = new OperatorPerformanceResponse();
            perf.setOperateurId(opId);
            perf.setOperateurName(null);

            when(supervisionService.getOperatorPerformance("7d")).thenReturn(List.of(perf));
            when(organisationClient.getUserById(opId)).thenThrow(new RuntimeException("Feign timeout"));

            ResponseEntity<List<OperatorPerformanceResponse>> response =
                    controller.getOperatorPerformance("7d");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get(0).getOperateurName())
                    .startsWith("Opérateur ");
        }

        @Test
        @DisplayName("Opérateur avec nom déjà défini → pas d'appel Feign")
        void alreadyHasName_noFeignCall() {
            OperatorPerformanceResponse perf = new OperatorPerformanceResponse();
            perf.setOperateurId(UUID.randomUUID());
            perf.setOperateurName("Déjà Résolu");

            when(supervisionService.getOperatorPerformance("30d")).thenReturn(List.of(perf));

            ResponseEntity<List<OperatorPerformanceResponse>> response =
                    controller.getOperatorPerformance("30d");

            assertThat(response.getBody().get(0).getOperateurName()).isEqualTo("Déjà Résolu");
            verifyNoInteractions(organisationClient);
        }
    }

    // ═══════════════════════════════════════
    // getSupervisionAlerts
    // ═══════════════════════════════════════

    @Test
    @DisplayName("GET /supervision/alerts → retourne la liste d'alertes")
    void getSupervisionAlerts_returnsList() {
        SupervisionAlert alert = new SupervisionAlert();
        when(supervisionService.getAlerts()).thenReturn(List.of(alert));

        ResponseEntity<List<SupervisionAlert>> response = controller.getSupervisionAlerts();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
    }
}
