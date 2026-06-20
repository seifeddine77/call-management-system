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
        @DisplayName("OPERATOR avec header → verrouillé sur l'agence du header (param ignoré)")
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

        @Test
        @DisplayName("Appel interne sans header (n8n/WhatsApp) → le param agenceId filtre réellement")
        void internalCall_noHeader_honoursAgenceIdParam() {
            UUID paramAgenceId = UUID.randomUUID();
            when(ticketService.getTicketStats(paramAgenceId)).thenReturn(new TicketStats());

            // Rôle par défaut OPERATOR, aucun X-User-Agence-Id : cas de l'appel direct n8n.
            ResponseEntity<TicketStats> response = controller.getTicketStats(
                    paramAgenceId, null, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).getTicketStats(paramAgenceId);
        }

        @Test
        @DisplayName("Deux agenceId différents → deux stats distinctes (le param est bien transmis)")
        void differentAgenceIds_routeToDifferentStats() {
            UUID agenceA = UUID.randomUUID();
            UUID agenceB = UUID.randomUUID();
            when(ticketService.getTicketStats(agenceA)).thenReturn(new TicketStats());
            when(ticketService.getTicketStats(agenceB)).thenReturn(new TicketStats());

            controller.getTicketStats(agenceA, null, ROLE_OPERATOR);
            controller.getTicketStats(agenceB, null, ROLE_OPERATOR);

            verify(ticketService).getTicketStats(agenceA);
            verify(ticketService).getTicketStats(agenceB);
            verify(ticketService, never()).getTicketStats(null);
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
        @DisplayName("OPERATOR avec header → verrouillé sur l'agence du header (param ignoré)")
        void operator_usesHeaderAgenceId() {
            UUID headerAgenceId = UUID.randomUUID();
            UUID paramAgenceId = UUID.randomUUID();
            QueueResponse queue = new QueueResponse();
            when(queueService.getQueue(headerAgenceId)).thenReturn(queue);

            ResponseEntity<QueueResponse> response = controller.getQueue(
                    paramAgenceId, headerAgenceId, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(queueService).getQueue(headerAgenceId);
        }

        @Test
        @DisplayName("Appel interne sans header (n8n/WhatsApp) → le param agenceId filtre réellement")
        void internalCall_noHeader_honoursAgenceIdParam() {
            UUID paramAgenceId = UUID.randomUUID();
            QueueResponse queue = new QueueResponse();
            when(queueService.getQueue(paramAgenceId)).thenReturn(queue);

            // Rôle par défaut OPERATOR, aucun X-User-Agence-Id : c'est le cas de l'appel direct n8n.
            ResponseEntity<QueueResponse> response = controller.getQueue(
                    paramAgenceId, null, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(queueService).getQueue(paramAgenceId);
        }

        @Test
        @DisplayName("Deux agenceId différents → deux files distinctes (le param est bien transmis)")
        void differentAgenceIds_routeToDifferentQueues() {
            UUID agenceA = UUID.randomUUID();
            UUID agenceB = UUID.randomUUID();
            when(queueService.getQueue(agenceA)).thenReturn(new QueueResponse());
            when(queueService.getQueue(agenceB)).thenReturn(new QueueResponse());

            controller.getQueue(agenceA, null, ROLE_OPERATOR);
            controller.getQueue(agenceB, null, ROLE_OPERATOR);

            verify(queueService).getQueue(agenceA);
            verify(queueService).getQueue(agenceB);
            verify(queueService, never()).getQueue(null);
        }

        @Test
        @DisplayName("Sans param ni header → file globale (param et header null)")
        void noParamNoHeader_returnsGlobalQueue() {
            when(queueService.getQueue(null)).thenReturn(new QueueResponse());

            ResponseEntity<QueueResponse> response = controller.getQueue(
                    null, null, ROLE_OPERATOR);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(queueService).getQueue(null);
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

    // ═══════════════════════════════════════
    // getTickets, getTicketById, createTicket, updateStatut, assignTicket
    // ═══════════════════════════════════════

    @Test
    @DisplayName("getTickets → appelle le service et retourne une page de tickets")
    void getTickets_returnsOk() {
        TicketFilter filter = new TicketFilter();
        UUID userId = UUID.randomUUID();
        UUID userAgenceId = UUID.randomUUID();
        org.springframework.data.domain.Page<TicketResponse> expectedPage = org.springframework.data.domain.Page.empty();
        when(ticketService.getTickets(filter, ROLE_OPERATOR, userId, userAgenceId)).thenReturn(expectedPage);

        ResponseEntity<org.springframework.data.domain.Page<TicketResponse>> response =
                controller.getTickets(filter, userId, ROLE_OPERATOR, userAgenceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedPage);
    }

    @Test
    @DisplayName("getTicketById → appelle le service et retourne le ticket")
    void getTicketById_returnsOk() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID userAgenceId = UUID.randomUUID();
        TicketResponse expectedTicket = new TicketResponse();
        when(ticketService.getTicketById(id, ROLE_OPERATOR, userId, userAgenceId)).thenReturn(expectedTicket);

        ResponseEntity<TicketResponse> response =
                controller.getTicketById(id, userId, ROLE_OPERATOR, userAgenceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedTicket);
    }

    @Test
    @DisplayName("createTicket → appelle le service et retourne le ticket créé")
    void createTicket_returnsCreated() {
        CreateTicketRequest request = new CreateTicketRequest();
        UUID userId = UUID.randomUUID();
        String userEmail = "test@user.com";
        TicketResponse expectedTicket = new TicketResponse();
        when(ticketService.createTicket(request, userId, userEmail)).thenReturn(expectedTicket);

        ResponseEntity<TicketResponse> response =
                controller.createTicket(request, userId, userEmail);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(expectedTicket);
    }

    @Test
    @DisplayName("updateStatut → appelle le service et retourne le ticket mis à jour")
    void updateStatut_returnsOk() {
        UUID id = UUID.randomUUID();
        UpdateStatutRequest request = new UpdateStatutRequest();
        UUID userId = UUID.randomUUID();
        String userEmail = "test@user.com";
        TicketResponse expectedTicket = new TicketResponse();
        when(ticketService.updateStatut(id, request, userId, userEmail)).thenReturn(expectedTicket);

        ResponseEntity<TicketResponse> response =
                controller.updateStatut(id, request, userId, userEmail);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedTicket);
    }

    @Test
    @DisplayName("assignTicket → appelle le service et retourne le ticket mis à jour")
    void assignTicket_returnsOk() {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        AssignTicketRequest request = new AssignTicketRequest(operatorId);
        UUID requesterId = UUID.randomUUID();
        UUID requesterAgenceId = UUID.randomUUID();
        TicketResponse expectedTicket = new TicketResponse();
        when(ticketService.assignTicket(id, operatorId, requesterId, ROLE_OPERATOR, requesterAgenceId)).thenReturn(expectedTicket);

        ResponseEntity<TicketResponse> response =
                controller.assignTicket(id, request, requesterId, ROLE_OPERATOR, requesterAgenceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedTicket);
    }

    // ═══════════════════════════════════════
    // uploadAudio
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("uploadAudio")
    class UploadAudio {
        @Test
        @DisplayName("Consentement absent ou false → log warning, exécute transcription, analyse, et chiffrement")
        void noConsentement_proceeds() throws Exception {
            UUID id = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("test.wav");
            when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

            UUID userId = UUID.randomUUID();
            String userName = "test@user.com";

            TicketResponse ticket = new TicketResponse();
            ticket.setId(id);
            ticket.setConsentementEnregistrement(false);
            
            // First call to check consent (with admin role)
            when(ticketService.getTicketById(id, ROLE_ADMIN, userId, null)).thenReturn(ticket);

            // Mock AI service
            when(aiServiceClient.transcribeAudio(file, id.toString()))
                    .thenReturn(Map.of("text", "Hello client", "file_path", "/audio/test.wav"));
            when(aiServiceClient.analyzeText(any()))
                    .thenReturn(Map.of("keywords", List.of("hello", "client"), "type_sinistre", "PANNE"));

            when(objectMapper.writeValueAsString(any())).thenReturn("[\"hello\",\"client\"]");

            // Mock Audio Encryption
            when(audioEncryptionService.isEnabled()).thenReturn(true);
            when(audioEncryptionService.encrypt(any())).thenReturn(new byte[]{4, 5, 6});

            ResponseEntity<TicketResponse> response = controller.uploadAudio(id, file, userId, userName);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).updateAudioTranscription(id, "/audio/test.wav.enc", "Hello client", "[\"hello\",\"client\"]");
        }

        @Test
        @DisplayName("Exception dans le service AI → fallback en stockant uniquement le chemin audio")
        void aiServiceFails_fallbackToPathOnly() throws Exception {
            UUID id = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("test.wav");

            UUID userId = UUID.randomUUID();
            String userName = "test@user.com";

            TicketResponse ticket = new TicketResponse();
            ticket.setId(id);
            ticket.setConsentementEnregistrement(true);

            when(ticketService.getTicketById(id, ROLE_ADMIN, userId, null)).thenReturn(ticket);
            when(aiServiceClient.transcribeAudio(any(), anyString())).thenThrow(new RuntimeException("AI down"));

            ResponseEntity<TicketResponse> response = controller.uploadAudio(id, file, userId, userName);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).updateAudioTranscription(eq(id), anyString(), eq(null), eq(null));
        }

        @Test
        @DisplayName("Exception dans le chiffrement → fallback sur le chemin non chiffré")
        void encryptionFails_fallbackToUnencrypted() throws Exception {
            UUID id = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("test.wav");
            when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

            UUID userId = UUID.randomUUID();
            String userName = "test@user.com";

            TicketResponse ticket = new TicketResponse();
            ticket.setId(id);
            ticket.setConsentementEnregistrement(true);

            when(ticketService.getTicketById(id, ROLE_ADMIN, userId, null)).thenReturn(ticket);
            when(aiServiceClient.transcribeAudio(file, id.toString()))
                    .thenReturn(Map.of("text", "Hello client", "file_path", "/audio/test.wav"));
            when(aiServiceClient.analyzeText(any()))
                    .thenReturn(Map.of("keywords", List.of("hello"), "type_sinistre", "PANNE"));
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"hello\"]");

            when(audioEncryptionService.isEnabled()).thenReturn(true);
            when(audioEncryptionService.encrypt(any())).thenThrow(new RuntimeException("Cipher error"));

            ResponseEntity<TicketResponse> response = controller.uploadAudio(id, file, userId, userName);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(ticketService).updateAudioTranscription(id, "/audio/test.wav", "Hello client", "[\"hello\"]");
        }
    }

    // ═══════════════════════════════════════
    // Photos management
    // ═══════════════════════════════════════

    @Nested
    @DisplayName("Photos management")
    class PhotosManagement {
        private final String tempStoragePath = "target/test-photos";

        @BeforeEach
        void initPhotosPath() {
            org.springframework.test.util.ReflectionTestUtils.setField(controller, "photosStoragePath", tempStoragePath);
        }

        @Test
        @DisplayName("processSinglePhoto avec content type invalide → ignore le fichier")
        void uploadPhotos_invalidContentType_ignoresFile() {
            UUID ticketId = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getContentType()).thenReturn("application/pdf");
            when(file.getOriginalFilename()).thenReturn("doc.pdf");

            ResponseEntity<TicketResponse> response = controller.uploadPhotos(ticketId, List.of(file), UUID.randomUUID());

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("processSinglePhoto avec taille trop grande → ignore le fichier")
        void uploadPhotos_tooLarge_ignoresFile() {
            UUID ticketId = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getOriginalFilename()).thenReturn("image.png");
            when(file.getSize()).thenReturn(11 * 1024 * 1024L); // 11MB

            ResponseEntity<TicketResponse> response = controller.uploadPhotos(ticketId, List.of(file), UUID.randomUUID());

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("uploadPhotos avec succès → sauvegarde sur disque et fusionne les chemins")
        void uploadPhotos_success() throws Exception {
            UUID ticketId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getOriginalFilename()).thenReturn("photo.jpg");
            when(file.getSize()).thenReturn(1024L);

            TicketResponse ticket = new TicketResponse();
            ticket.setId(ticketId);
            ticket.setPhotosPaths("[\"/photos/old.jpg\"]");

            when(ticketService.getTicketById(ticketId, "ADMIN", userId, null)).thenReturn(ticket);
            when(objectMapper.readValue(anyString(), eq(List.class))).thenReturn(new java.util.ArrayList<>(List.of("/photos/old.jpg")));
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"/photos/old.jpg\",\"/photos/" + ticketId + "/new.jpg\"]");

            ResponseEntity<TicketResponse> response = controller.uploadPhotos(ticketId, List.of(file), userId);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(file).transferTo(any(java.io.File.class));
            verify(ticketService).updatePhotosPaths(eq(ticketId), anyString());
        }

        @Test
        @DisplayName("servePhoto retourne le fichier s'il existe")
        void servePhoto_exists() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Paths.get(tempStoragePath, "123");
            java.nio.file.Files.createDirectories(tempDir);
            java.nio.file.Path tempFile = tempDir.resolve("test.png");
            java.nio.file.Files.write(tempFile, new byte[]{1, 2, 3});

            ResponseEntity<org.springframework.core.io.Resource> response = controller.servePhoto("123", "test.png");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");

            // Cleanup
            java.nio.file.Files.deleteIfExists(tempFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }

        @Test
        @DisplayName("servePhoto retourne 404 si le fichier n'existe pas")
        void servePhoto_notExists() {
            ResponseEntity<org.springframework.core.io.Resource> response = controller.servePhoto("non-existent", "photo.png");
            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("processSinglePhoto lève IOException → log et retourne null")
        void uploadPhotos_transferToThrowsIOException() throws Exception {
            UUID ticketId = UUID.randomUUID();
            org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getOriginalFilename()).thenReturn("photo.png");
            doThrow(new java.io.IOException("Disk full")).when(file).transferTo(any(java.io.File.class));

            ResponseEntity<TicketResponse> response = controller.uploadPhotos(ticketId, List.of(file), UUID.randomUUID());

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }
}
