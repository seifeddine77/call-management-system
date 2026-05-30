package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.config.TwilioConfig;
import com.twilio.security.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Service de gestion Twilio VoIP.
 * - Validation des signatures webhook (anti-spoofing)
 * - Téléchargement des enregistrements audio
 * - Génération de réponses TwiML
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwilioService {

    private final TwilioConfig twilioConfig;

    // SonarCloud C3 fix — HttpClient as reusable final field (no resource leak)
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Valide la signature d'un webhook Twilio pour s'assurer
     * que la requête provient bien de Twilio et non d'un tiers.
     */
    public boolean validateRequest(String signature, String url, Map<String, String> params) {
        if (!twilioConfig.isEnabled()) {
            log.warn("Twilio not configured — skipping signature validation");
            return true; // En mode dev, on accepte tout
        }
        try {
            RequestValidator validator = new RequestValidator(twilioConfig.getAuthToken());
            return validator.validate(url, params, signature);
        } catch (Exception e) {
            log.error("Twilio signature validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Télécharge un enregistrement audio depuis Twilio.
     * Les enregistrements sont accessibles via une URL authentifiée.
     *
     * @param recordingUrl URL Twilio de l'enregistrement (sans .json)
     * @return contenu audio en bytes
     */
    public byte[] downloadRecording(String recordingUrl) throws IOException, InterruptedException {
        // Twilio fournit l'URL sans protocole, il faut ajouter https:
        String fullUrl = recordingUrl.startsWith("http")
                ? recordingUrl
                : "https://api.twilio.com" + recordingUrl;

        // Ajouter .wav pour obtenir le format WAV
        if (!fullUrl.contains(".wav") && !fullUrl.contains(".mp3")) {
            fullUrl += ".wav";
        }

        log.info("📥 Downloading Twilio recording from: {}", fullUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", "Basic " +
                        java.util.Base64.getEncoder().encodeToString(
                                (twilioConfig.getAccountSid() + ":" + twilioConfig.getAuthToken()).getBytes()))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            log.info("✅ Recording downloaded: {} bytes", response.body().length);
            return response.body();
        } else {
            throw new IOException("Failed to download recording: HTTP " + response.statusCode());
        }
    }

    /**
     * Génère une réponse TwiML pour un appel entrant.
     * - Message d'accueil vocal
     * - Démarrage de l'enregistrement
     * - Callback de fin d'enregistrement
     */
    public String generateVoiceResponse(String ticketNumero) {
        // action = lightweight endpoint called immediately when caller stops speaking
        // recordingStatusCallback = called when the audio file is ACTUALLY READY for download
        String actionUrl = twilioConfig.getWebhookBaseUrl() + "/api/channels/twilio/recording-done";
        String recordingReadyUrl = twilioConfig.getWebhookBaseUrl() + "/api/channels/twilio/recording";
        String statusUrl = twilioConfig.getWebhookBaseUrl() + "/api/channels/twilio/status";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say language="fr-FR" voice="alice">
                        Bienvenue au service client. Votre demande numéro %s a été enregistrée.
                        Veuillez décrire votre problème après le bip. Nous enregistrons cet appel pour améliorer notre service.
                    </Say>
                    <Record
                        maxLength="300"
                        action="%s"
                        recordingStatusCallback="%s"
                        recordingStatusCallbackMethod="POST"
                        statusCallback="%s"
                        statusCallbackMethod="POST"
                        transcribe="false"
                        playBeep="true"
                        trim="trim-silence" />
                    <Say language="fr-FR" voice="alice">
                        Merci pour votre appel. Un opérateur vous recontactera sous peu.
                    </Say>
                </Response>
                """
                .formatted(ticketNumero, actionUrl, recordingReadyUrl, statusUrl);
    }

    /**
     * Génère une réponse TwiML simple après l'enregistrement.
     */
    public String generateRecordingCompleteResponse() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Say language="fr-FR" voice="alice">
                        Merci. Votre message a bien été enregistré. Un opérateur traitera votre demande rapidement. Au revoir.
                    </Say>
                    <Hangup/>
                </Response>
                """;
    }
}
