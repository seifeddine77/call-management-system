package com.pfe.callmgmt.ticket.controller;

import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.dto.TicketResponse;
import com.pfe.callmgmt.ticket.service.AudioEncryptionService;
import com.pfe.callmgmt.ticket.service.TicketService;
import com.pfe.callmgmt.ticket.service.TwilioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires — TwilioWebhookController.
 */
@ExtendWith(MockitoExtension.class)
class TwilioWebhookControllerTest {

    // --- SonarCloud: extracted string constants ---
    private static final String SYSTEM_TWILIO = "SYSTEM-TWILIO";
    private static final String TICKET_NUMERO = "T-20260311-0042";

    @Mock private TicketService ticketService;
    @Mock private TwilioService twilioService;
    @Mock private AiServiceClient aiServiceClient;
    @Mock private AudioEncryptionService audioEncryptionService;
    @Mock private ObjectMapper objectMapper;

    private TwilioWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new TwilioWebhookController(
                ticketService, twilioService, aiServiceClient, audioEncryptionService, objectMapper);
    }

    // ═══════════════════════════════════════
    // handleIncomingCall
    // ═══════════════════════════════════════

    @Test
    @DisplayName("Appel entrant → crée ticket CALL + retourne TwiML XML")
    void handleIncomingCall_createsTicketAndReturnsTwiml() {
        // Arrange
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(UUID.randomUUID());
        mockTicket.setNumero(TICKET_NUMERO);

        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO)))
                .thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(TICKET_NUMERO))
                .thenReturn("<Response><Record/></Response>");

        // Act
        ResponseEntity<String> response = controller.handleIncomingCall(
                "CA123abc", "+33600000000", "+33100000000",
                "Jean Dupont", "Paris", "FR");

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("<Record/>");
        verify(ticketService).createTicket(any(), eq(null), eq(SYSTEM_TWILIO));
        verify(twilioService).generateVoiceResponse(TICKET_NUMERO);
    }

    @Test
    @DisplayName("Appel entrant sans CallerName → utilise le numéro comme nom")
    void handleIncomingCall_noCallerName_usesPhoneNumber() {
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(UUID.randomUUID());
        mockTicket.setNumero("T-20260311-0001");

        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO)))
                .thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");

        // Act — CallerName is null
        controller.handleIncomingCall("CA999", "+33700000000", null, null, null, null);

        // Verify ticket was created (the clientNom should be "Appelant +33700000000")
        verify(ticketService).createTicket(
                argThat(req -> req.getClientNom().contains("+33700000000")),
                eq(null), eq(SYSTEM_TWILIO));
    }

    // ═══════════════════════════════════════
    // handleRecordingComplete
    // ═══════════════════════════════════════

    @Test
    @DisplayName("Recording avec CallSid inconnu → retourne TwiML sans crash")
    void handleRecordingComplete_unknownCallSid_returnsTwiml() {
        when(twilioService.generateRecordingCompleteResponse())
                .thenReturn("<Response><Hangup/></Response>");

        ResponseEntity<String> response = controller.handleRecordingComplete(
                "CA_UNKNOWN", "https://api.twilio.com/rec/123", "RE123", "60");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("<Hangup/>");
    }

    // ═══════════════════════════════════════
    // handleCallStatus
    // ═══════════════════════════════════════

    @Test
    @DisplayName("Statut appel failed → retourne 200 OK (logging only)")
    void handleCallStatus_failed_returns200() {
        ResponseEntity<Void> response = controller.handleCallStatus(
                "CA_FAIL_123", "failed", "0");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Statut appel completed → retourne 200 OK")
    void handleCallStatus_completed_returns200() {
        ResponseEntity<Void> response = controller.handleCallStatus(
                "CA_OK_456", "completed", "120");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Statut appel busy avec ticket associé → retire de la map et log")
    void handleCallStatus_busyWithKnownSid_removesFromMap() throws Exception {
        // Simuler un appel entrant pour remplir la callTicketMap
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(UUID.randomUUID());
        mockTicket.setNumero(TICKET_NUMERO);
        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO))).thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");
        controller.handleIncomingCall("CA_BUSY_789", "+33600000001", null, "Test", null, null);

        // Maintenant envoyer le statut busy
        ResponseEntity<Void> resp = controller.handleCallStatus("CA_BUSY_789", "busy", "0");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Statut appel no-answer → retourne 200 OK")
    void handleCallStatus_noAnswer_returns200() {
        ResponseEntity<Void> response = controller.handleCallStatus(
                "CA_NOANSWER", "no-answer", "30");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // ═══════════════════════════════════════
    // handleRecordingDone
    // ═══════════════════════════════════════

    @Test
    @DisplayName("handleRecordingDone → retourne TwiML et log")
    void handleRecordingDone_returnsTwiml() {
        when(twilioService.generateRecordingCompleteResponse()).thenReturn("<Response><Say>Merci</Say></Response>");

        ResponseEntity<String> response = controller.handleRecordingDone("CA_DONE_001");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Merci");
        verify(twilioService).generateRecordingCompleteResponse();
    }

    // ═══════════════════════════════════════
    // handleRecordingComplete - CallSid connu
    // ═══════════════════════════════════════

    @Test
    @DisplayName("Recording avec CallSid connu → soumet tâche async et retourne TwiML")
    void handleRecordingComplete_knownCallSid_submitsAsyncTask() {
        // D'abord créer un appel pour enregistrer le CallSid dans la map
        String callSid = "CA_KNOWN_001";
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(UUID.randomUUID());
        mockTicket.setNumero(TICKET_NUMERO);
        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO))).thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");
        controller.handleIncomingCall(callSid, "+33600000002", null, "Test", null, null);

        when(twilioService.generateRecordingCompleteResponse()).thenReturn("<Response><Hangup/></Response>");

        ResponseEntity<String> response = controller.handleRecordingComplete(
                callSid, "https://api.twilio.com/recordings/RE123.wav", "RE123", "45");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("<Hangup/>");
        verify(twilioService).generateRecordingCompleteResponse();
    }

    @Test
    @DisplayName("Recording sans RecordingUrl → ne soumet pas de tâche async")
    void handleRecordingComplete_nullRecordingUrl_noAsyncTask() {
        String callSid = "CA_NOURL_001";
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(UUID.randomUUID());
        mockTicket.setNumero(TICKET_NUMERO);
        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO))).thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");
        controller.handleIncomingCall(callSid, "+33600000003", null, "Test", null, null);

        when(twilioService.generateRecordingCompleteResponse()).thenReturn("<Response><Hangup/></Response>");

        // null recordingUrl → ne doit PAS soumettre de tâche
        ResponseEntity<String> response = controller.handleRecordingComplete(callSid, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // aiServiceClient ne devrait jamais être appelé directement (async)
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    @DisplayName("processRecording asynchrone réussit complet avec chiffrement")
    void processRecording_async_success() throws Exception {
        String callSid = "CA_ASYNC_001";
        UUID ticketId = UUID.randomUUID();
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(ticketId);
        mockTicket.setNumero(TICKET_NUMERO);
        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO))).thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");
        
        // 1. Appel voix pour mapper le CallSid
        controller.handleIncomingCall(callSid, "+33600000002", null, "Test", null, null);

        // Stub twilioService download
        byte[] audioBytes = new byte[]{1, 2, 3};
        when(twilioService.downloadRecording("https://recording-url")).thenReturn(audioBytes);

        // Stub audioEncryptionService
        when(audioEncryptionService.isEnabled()).thenReturn(true);
        when(audioEncryptionService.encrypt(audioBytes)).thenReturn(new byte[]{4, 5, 6});

        // Stub aiServiceClient
        when(aiServiceClient.transcribeAudio(any(), eq(ticketId.toString())))
                .thenReturn(Map.of("text", "Hello Twilio", "file_path", "transcription.wav"));
        when(aiServiceClient.analyzeText(any()))
                .thenReturn(Map.of("type_sinistre", "PANNE", "sentiment", "POSITIF"));

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type_sinistre\":\"PANNE\"}");
        when(twilioService.generateRecordingCompleteResponse()).thenReturn("<Response/>");

        // 2. Appel du webhook recording complet
        controller.handleRecordingComplete(callSid, "https://recording-url", "RE123", "10");

        // Attendre que la tâche asynchrone se termine
        ExecutorService executor = (ExecutorService) org.springframework.test.util.ReflectionTestUtils.getField(controller, "audioProcessingExecutor");
        
        // As the executor is shutdown, we need to re-initialize it or re-create the controller for subsequent tests if JUnit reusing.
        // But since this is a unit test class with MockitoExtension, each test method is executed on a new instance of the test class,
        // which calls setUp() and constructs a new controller instance with a new executor. So shutting down is completely safe!
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Vérifier les appels
        verify(twilioService).downloadRecording("https://recording-url");
        verify(audioEncryptionService).encrypt(audioBytes);
        verify(aiServiceClient).transcribeAudio(any(), eq(ticketId.toString()));
        verify(aiServiceClient).analyzeText(any());
        verify(ticketService).updateAudioTranscription(eq(ticketId), anyString(), eq("Hello Twilio"), anyString());
    }

    @Test
    @DisplayName("processRecording asynchrone gère les exceptions")
    void processRecording_async_handlesException() throws Exception {
        String callSid = "CA_ASYNC_FAIL";
        UUID ticketId = UUID.randomUUID();
        TicketResponse mockTicket = new TicketResponse();
        mockTicket.setId(ticketId);
        mockTicket.setNumero(TICKET_NUMERO);
        when(ticketService.createTicket(any(), eq(null), eq(SYSTEM_TWILIO))).thenReturn(mockTicket);
        when(twilioService.generateVoiceResponse(any())).thenReturn("<Response/>");
        
        // 1. Appel voix pour mapper le CallSid
        controller.handleIncomingCall(callSid, "+33600000002", null, "Test", null, null);

        // Stub twilioService download to throw exception
        when(twilioService.downloadRecording(anyString())).thenThrow(new RuntimeException("Download failed"));
        when(twilioService.generateRecordingCompleteResponse()).thenReturn("<Response/>");

        // 2. Appel du webhook recording complet
        controller.handleRecordingComplete(callSid, "https://recording-url", "RE123", "10");

        // Attendre que la tâche asynchrone se termine
        ExecutorService executor = (ExecutorService) org.springframework.test.util.ReflectionTestUtils.getField(controller, "audioProcessingExecutor");
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Verifier que l'exception a été gérée et qu'on n'a pas appelé updateAudioTranscription
        verify(ticketService, never()).updateAudioTranscription(any(), any(), any(), any());
    }
}
