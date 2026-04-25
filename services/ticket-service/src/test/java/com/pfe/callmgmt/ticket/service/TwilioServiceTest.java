package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.config.TwilioConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires — TwilioService (TwiML + validation).
 */
class TwilioServiceTest {

    private TwilioConfig config;
    private TwilioService twilioService;

    @BeforeEach
    void setUp() {
        config = new TwilioConfig();
        config.setWebhookBaseUrl("https://example.ngrok.io");
        config.setAccountSid("ACtest123456789");
        config.setAuthToken("test_auth_token");
        config.setPhoneNumber("+15005550006");
        twilioService = new TwilioService(config);
    }

    // ═══════════════════════════════════════
    // generateVoiceResponse
    // ═══════════════════════════════════════

    @Test
    @DisplayName("TwiML contient <Record> avec maxLength et callback")
    void voiceResponse_containsRecord() {
        String twiml = twilioService.generateVoiceResponse("T-20260311-0001");

        assertThat(twiml).contains("<Record");
        assertThat(twiml).contains("maxLength=\"300\"");
        assertThat(twiml).contains("playBeep=\"true\"");
        assertThat(twiml).contains("trim=\"trim-silence\"");
        assertThat(twiml).contains("https://example.ngrok.io/api/channels/twilio/recording");
    }

    @Test
    @DisplayName("TwiML contient le numéro du ticket dans le message vocal")
    void voiceResponse_containsTicketNumero() {
        String twiml = twilioService.generateVoiceResponse("T-20260311-4242");

        assertThat(twiml).contains("T-20260311-4242");
        assertThat(twiml).contains("<Say language=\"fr-FR\"");
    }

    @Test
    @DisplayName("TwiML est un XML valide avec <Response> racine")
    void voiceResponse_isValidXml() {
        String twiml = twilioService.generateVoiceResponse("T-20260311-0001");

        assertThat(twiml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(twiml).contains("<Response>");
        assertThat(twiml).contains("</Response>");
    }

    // ═══════════════════════════════════════
    // generateRecordingCompleteResponse
    // ═══════════════════════════════════════

    @Test
    @DisplayName("TwiML de fin contient <Hangup/>")
    void recordingComplete_containsHangup() {
        String twiml = twilioService.generateRecordingCompleteResponse();

        assertThat(twiml).contains("<Hangup/>");
        assertThat(twiml).contains("<Say language=\"fr-FR\"");
        assertThat(twiml).contains("</Response>");
    }

    // ═══════════════════════════════════════
    // validateRequest
    // ═══════════════════════════════════════

    @Test
    @DisplayName("Validation désactivée quand Twilio non configuré → retourne true")
    void validateRequest_disabledReturnsTrue() {
        TwilioConfig disabledConfig = new TwilioConfig();
        // Pas de accountSid ni authToken → isEnabled() = false
        TwilioService disabledService = new TwilioService(disabledConfig);

        boolean result = disabledService.validateRequest("fake-sig", "http://example.com", java.util.Map.of());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Validation avec mauvaise signature → retourne false")
    void validateRequest_badSignatureReturnsFalse() {
        boolean result = twilioService.validateRequest(
                "invalid-signature",
                "http://example.com/webhook",
                java.util.Map.of("CallSid", "CA123")
        );

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isEnabled retourne true quand les credentials sont configurés")
    void isEnabled_withCredentials() {
        assertThat(config.isEnabled()).isTrue();
    }
}
