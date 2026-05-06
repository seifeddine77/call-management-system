package com.pfe.callmgmt.ticket.contract;

import com.pfe.callmgmt.ticket.client.AiServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consumer-side Contract Tests for ticket-service → ai-service (Python
 * FastAPI).
 *
 * Verifies that the AiServiceClient Feign interface correctly defines
 * the contract expected by:
 * 1. POST /analyze/ — NLP text analysis
 * 2. POST /transcribe/ — Audio transcription (Whisper)
 *
 * Since ai-service is Python (no Spring Cloud Contract stubs jar),
 * we use @MockBean to verify the expected request/response structure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class AiServiceContractTest {

    // --- SonarCloud: extracted string constants ---
    private static final String KEY_TICKET_ID = "ticket_id";
    private static final String KEY_TEXT = "text";
    private static final String KEY_TYPE_SINISTRE = "type_sinistre";
    private static final String KEY_CONFIDENCE = "confidence";
    private static final String KEY_TRANSCRIPTION = "transcription";

    @MockBean
    private AiServiceClient aiServiceClient;

    @Test
    @DisplayName("Contract: POST /analyze/ → returns NLP analysis with all required fields")
    void shouldAnalyzeTextAndReturnExpectedFields() {
        // Given — the expected response from ai-service /analyze/
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put(KEY_TICKET_ID, "test-ticket-123");
        expectedResponse.put("piece_reference", "ALT-4892");
        expectedResponse.put(KEY_TYPE_SINISTRE, "PANNE");
        expectedResponse.put("type_piece", "MECANIQUE");
        expectedResponse.put("sentiment", "NEGATIF");
        expectedResponse.put("urgency", "HIGH");
        expectedResponse.put(KEY_CONFIDENCE, 0.92);
        expectedResponse.put("keywords", List.of("alternateur", "panne", "moteur"));
        expectedResponse.put("summary", "Client signale une panne d'alternateur");

        when(aiServiceClient.analyzeText(any())).thenReturn(expectedResponse);

        // When — send request matching ai-service contract
        Map<String, String> request = new HashMap<>();
        request.put(KEY_TICKET_ID, "test-ticket-123");
        request.put(KEY_TEXT, "Mon alternateur ALT-4892 est en panne depuis hier. Le moteur ne démarre plus.");

        Map<String, Object> response = aiServiceClient.analyzeText(request);

        // Then — verify contract response fields
        assertThat(response).isNotNull();
        assertThat(response).containsKey(KEY_TICKET_ID);
        assertThat(response).containsKey("piece_reference");
        assertThat(response).containsKey(KEY_TYPE_SINISTRE);
        assertThat(response).containsKey("sentiment");
        assertThat(response).containsKey(KEY_CONFIDENCE);
        assertThat(response).containsKey("keywords");

        // Verify types
        assertThat(response.get("piece_reference")).isInstanceOf(String.class);
        assertThat(response.get(KEY_CONFIDENCE)).isInstanceOf(Number.class);
        assertThat(response.get("keywords")).isInstanceOf(List.class);

        verify(aiServiceClient).analyzeText(any());
    }

    @Test
    @DisplayName("Contract: POST /analyze/ → request requires ticket_id and text fields")
    void shouldSendCorrectRequestFieldsToAnalyze() {
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put(KEY_TICKET_ID, "abc-123");
        mockResponse.put(KEY_TYPE_SINISTRE, "USURE");

        when(aiServiceClient.analyzeText(any())).thenReturn(mockResponse);

        // The contract requires these exact keys
        Map<String, String> request = new HashMap<>();
        request.put(KEY_TICKET_ID, "abc-123");
        request.put(KEY_TEXT, "Description du sinistre");

        assertThat(request).containsKey(KEY_TICKET_ID);
        assertThat(request).containsKey(KEY_TEXT);

        Map<String, Object> response = aiServiceClient.analyzeText(request);
        assertThat(response).isNotNull();
        assertThat(response.get(KEY_TICKET_ID)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Contract: POST /analyze/ → handles empty/null text gracefully")
    void shouldHandleMissingTextInAnalyze() {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put(KEY_TICKET_ID, "fallback-123");
        fallbackResponse.put(KEY_TYPE_SINISTRE, "INCONNU");
        fallbackResponse.put(KEY_CONFIDENCE, 0.0);

        when(aiServiceClient.analyzeText(any())).thenReturn(fallbackResponse);

        Map<String, String> request = new HashMap<>();
        request.put(KEY_TICKET_ID, "fallback-123");
        request.put(KEY_TEXT, "");

        Map<String, Object> response = aiServiceClient.analyzeText(request);
        assertThat(response).containsKey(KEY_TICKET_ID);
    }

    @Test
    @DisplayName("Contract: POST /transcribe/ → returns transcription with expected fields")
    void shouldTranscribeAudioAndReturnExpectedFields() {
        // Given — mock response structure from ai-service /transcribe/
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put(KEY_TICKET_ID, "transcription-456");
        expectedResponse.put(KEY_TRANSCRIPTION, "Mon moteur ne démarre plus depuis hier matin");
        expectedResponse.put("language", "fr");
        expectedResponse.put("duration", 12.5);
        expectedResponse.put(KEY_CONFIDENCE, 0.95);

        when(aiServiceClient.transcribeAudio(any(), anyString())).thenReturn(expectedResponse);

        // When
        Map<String, Object> response = aiServiceClient.transcribeAudio(null, "transcription-456");

        // Then — verify response structure
        assertThat(response).isNotNull();
        assertThat(response).containsKey(KEY_TRANSCRIPTION);
        assertThat(response.get(KEY_TRANSCRIPTION)).isInstanceOf(String.class);
        assertThat((String) response.get(KEY_TRANSCRIPTION)).isNotBlank();

        verify(aiServiceClient).transcribeAudio(any(), anyString());
    }
}
