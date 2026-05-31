package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.dto.CreateTicketRequest;
import com.pfe.callmgmt.ticket.dto.TicketResponse;
import com.pfe.callmgmt.ticket.service.AudioEncryptionService;
import com.pfe.callmgmt.ticket.service.TicketService;
import com.pfe.callmgmt.ticket.service.TwilioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contrôleur Twilio Webhooks — Intégration VoIP réelle.
 *
 * Twilio envoie des requêtes POST avec des paramètres form-urlencoded.
 * 3 endpoints :
 * 1. /voice — Appel entrant → crée ticket + démarre enregistrement
 * 2. /recording — Enregistrement terminé → télécharge audio + transcription
 * 3. /status — Statut de l'appel (optionnel, pour logging)
 */
@Slf4j
@RestController
@RequestMapping("/api/channels/twilio")
@Tag(name = "Twilio VoIP", description = "Webhooks Twilio pour appels téléphoniques entrants")
public class TwilioWebhookController {

    private final TicketService ticketService;
    private final TwilioService twilioService;
    private final AiServiceClient aiServiceClient;
    private final AudioEncryptionService audioEncryptionService;
    // SonarCloud C5 fix — ObjectMapper injected (not created per call)
    private final ObjectMapper objectMapper;
    // SonarCloud C4 fix — ExecutorService instead of raw new Thread() (S2142)
    private final ExecutorService audioProcessingExecutor = Executors.newFixedThreadPool(4);

    @Value("${email.listener.default-agence-id:}")
    private String defaultAgenceId;

    public TwilioWebhookController(TicketService ticketService, TwilioService twilioService,
            AiServiceClient aiServiceClient,
            AudioEncryptionService audioEncryptionService, ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.twilioService = twilioService;
        this.aiServiceClient = aiServiceClient;
        this.audioEncryptionService = audioEncryptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Cache temporaire CallSid → TicketId pour lier l'enregistrement au bon ticket.
     */
    private final Map<String, UUID> callTicketMap = new ConcurrentHashMap<>();

    // ════════════════════════════════════════════════════════════════
    // WEBHOOK 1 — APPEL ENTRANT (Voice)
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/voice", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Webhook Twilio — Appel entrant (crée ticket + enregistre)")
    public ResponseEntity<String> handleIncomingCall(
            @RequestParam("CallSid") String callSid,
            @RequestParam("From") String from,
            @RequestParam(value = "To", required = false) String to,
            @RequestParam(value = "CallerName", required = false) String callerName,
            @RequestParam(value = "FromCity", required = false) String fromCity,
            @RequestParam(value = "FromCountry", required = false) String fromCountry) {

        log.info("📞 [TWILIO] Appel entrant — CallSid: {}, From: {}, CallerName: {}",
                callSid, from, callerName);

        // Créer un ticket pour cet appel
        CreateTicketRequest ticketReq = new CreateTicketRequest();
        ticketReq.setClientNom(callerName != null ? callerName : "Appelant " + from);
        ticketReq.setClientTel(from);
        ticketReq.setCanal("CALL");
        ticketReq.setDescription(String.format(
                "Appel Twilio entrant — CallSid: %s, De: %s%s%s",
                callSid, from,
                fromCity != null ? ", Ville: " + fromCity : "",
                fromCountry != null ? ", Pays: " + fromCountry : ""));
        ticketReq.setPriorite((short) 2); // Appels = priorité élevée

        // Affecter l'agence par défaut
        if (defaultAgenceId != null && !defaultAgenceId.isBlank()) {
            ticketReq.setAgenceId(UUID.fromString(defaultAgenceId));
        }

        TicketResponse ticket = ticketService.createTicket(ticketReq, null, "SYSTEM-TWILIO");

        // Sauvegarder le mapping CallSid → TicketId
        callTicketMap.put(callSid, ticket.getId());

        log.info("📞 [TWILIO] Ticket {} créé pour appel {}", ticket.getNumero(), callSid);

        // Retourner TwiML (message d'accueil + enregistrement)
        String twiml = twilioService.generateVoiceResponse(ticket.getNumero());
        return ResponseEntity.ok(twiml);
    }

    // ════════════════════════════════════════════════════════════════
    // WEBHOOK 2 — ENREGISTREMENT TERMINÉ (Recording)
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/recording", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Webhook Twilio — Enregistrement terminé (transcription auto)")
    public ResponseEntity<String> handleRecordingComplete(
            @RequestParam("CallSid") String callSid,
            @RequestParam(value = "RecordingUrl", required = false) String recordingUrl,
            @RequestParam(value = "RecordingSid", required = false) String recordingSid,
            @RequestParam(value = "RecordingDuration", required = false) String duration) {

        log.info("🎙️ [TWILIO] Enregistrement terminé — CallSid: {}, RecordingSid: {}, Durée: {}s, URL: {}",
                callSid, recordingSid, duration, recordingUrl);

        // Retrouver le ticket associé
        UUID ticketId = callTicketMap.get(callSid);
        if (ticketId == null) {
            log.warn("⚠️ [TWILIO] Aucun ticket trouvé pour CallSid: {}", callSid);
            return ResponseEntity.ok(twilioService.generateRecordingCompleteResponse());
        }

        // Télécharger et transcrire en asynchrone
        // SonarCloud C4 fix — ExecutorService au lieu de raw Thread (S2142)
        if (recordingUrl != null) {
            audioProcessingExecutor.submit(() -> processRecording(ticketId, recordingUrl, callSid));
        }

        // Retourner TwiML de fin
        return ResponseEntity.ok(twilioService.generateRecordingCompleteResponse());
    }

    /**
     * Télécharge l'enregistrement Twilio, l'envoie à l'AI service pour
     * transcription + NLP, et met à jour le ticket.
     */
    private void processRecording(UUID ticketId, String recordingUrl, String callSid) {
        try {
            log.info("🔄 [TWILIO] Traitement enregistrement pour ticket {}", ticketId);

            // 1. Télécharger l'audio depuis Twilio
            byte[] audioBytes = twilioService.downloadRecording(recordingUrl);

            // 1.5 Chiffrer l'audio (RGPD) — noop si pas de clé configurée
            if (audioEncryptionService.isEnabled()) {
                audioBytes = audioEncryptionService.encrypt(audioBytes);
                log.info("🔐 [TWILIO] Audio chiffré pour ticket {} ({} bytes)", ticketId, audioBytes.length);
            }

            // 2. Créer un MultipartFile pour le pipeline existant
            final byte[] bytes = audioBytes;
            final String filename = "twilio_" + callSid + ".wav";
            MultipartFile audioFile = new CustomMultipartFile(bytes, filename, "audio/wav");

            // 3. Envoyer au AI service pour transcription via Feign
            Map<String, Object> transcriptionResult = aiServiceClient.transcribeAudio(
                    audioFile, ticketId.toString());

            log.info("✅ [TWILIO] Transcription reçue pour ticket {}: {}",
                    ticketId, transcriptionResult.get("text"));

            // 4. Envoyer pour analyse NLP
            String transcription = (String) transcriptionResult.getOrDefault("text", "");
            if (!transcription.isBlank()) {
                Map<String, String> analyzeReq = Map.of(
                        "ticket_id", ticketId.toString(),
                        "text", transcription);
                Map<String, Object> analysisResult = aiServiceClient.analyzeText(analyzeReq);

                // 5. Mettre à jour le ticket avec les résultats
                // SonarCloud C5 fix — objectMapper injecté (pas de new ObjectMapper())
                String keywordsJson = objectMapper.writeValueAsString(analysisResult);
                ticketService.updateAudioTranscription(ticketId, audioFile.getOriginalFilename(),
                        transcription, keywordsJson);

                log.info("✅ [TWILIO] Pipeline complet pour ticket {} — sinistre: {}, sentiment: {}",
                        ticketId,
                        analysisResult.get("type_sinistre"),
                        analysisResult.get("sentiment"));
            }
        } catch (InterruptedException e) {
            log.error("❌ [TWILIO] Thread interrompu lors du traitement de l'enregistrement pour le ticket {}: {}",
                    ticketId, e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ [TWILIO] Erreur traitement enregistrement pour ticket {}: {}",
                    ticketId, e.getMessage(), e);
        } finally {
            // Nettoyer le cache
            callTicketMap.remove(callSid);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WEBHOOK 2b — RECORDING DONE (action callback — lightweight)
    // Called immediately when the caller stops speaking.
    // Just returns TwiML "merci" — actual processing is on /recording.
    // ════════════════════════════════════════════════════════════════

    @PostMapping(value = "/recording-done", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Webhook Twilio — Enregistrement terminé (réponse vocale)")
    public ResponseEntity<String> handleRecordingDone(
            @RequestParam("CallSid") String callSid) {

        log.info("🎙️ [TWILIO] Recording stopped for CallSid: {} — awaiting file ready callback", callSid);
        return ResponseEntity.ok(twilioService.generateRecordingCompleteResponse());
    }

    // ════════════════════════════════════════════════════════════════
    // WEBHOOK 3 — STATUT APPEL (Status Callback)
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/status")
    @Operation(summary = "Webhook Twilio — Statut de l'appel (logging)")
    public ResponseEntity<Void> handleCallStatus(
            @RequestParam("CallSid") String callSid,
            @RequestParam("CallStatus") String callStatus,
            @RequestParam(value = "CallDuration", required = false) String duration) {

        log.info("📊 [TWILIO] Statut appel — CallSid: {}, Statut: {}, Durée: {}s",
                callSid, callStatus, duration);

        // Si l'appel a échoué, on peut mettre à jour le ticket
        if ("failed".equals(callStatus) || "busy".equals(callStatus) || "no-answer".equals(callStatus)) {
            UUID ticketId = callTicketMap.remove(callSid);
            if (ticketId != null) {
                log.warn("⚠️ [TWILIO] Appel {} échoué ({}). Ticket {} conservé en NOUVEAU.",
                        callSid, callStatus, ticketId);
            }
        }

        return ResponseEntity.ok().build();
    }

    private static class CustomMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String filename;
        private final String contentType;

        public CustomMultipartFile(byte[] bytes, String filename, String contentType) {
            this.bytes = bytes;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
