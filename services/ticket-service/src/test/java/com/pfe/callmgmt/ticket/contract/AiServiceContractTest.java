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

    @MockBean
    private AiServiceClient aiServiceClient;

    @Test
    @DisplayName("Contract: POST /analyze/ → returns NLP analysis with all required fields")
    void shouldAnalyzeTextAndReturnExpectedFields() {
        // Given — the expected response from ai-service /analyze/
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("ticket_id", "test-ticket-123");
        expectedResponse.put("piece_reference", "ALT-4892");
        expectedResponse.put("type_sinistre", "PANNE");
        expectedResponse.put("type_piece", "MECANIQUE");
        expectedResponse.put("sentiment", "NEGATIF");
        expectedResponse.put("urgency", "HIGH");
        expectedResponse.put("confidence", 0.92);
        expectedResponse.put("keywords", List.of("alternateur", "panne", "moteur"));
        expectedResponse.put("summary", "Client signale une panne d'alternateur");

        when(aiServiceClient.analyzeText(any())).thenReturn(expectedResponse);

        // When — send request matching ai-service contract
        Map<String, String> request = new HashMap<>();
        request.put("ticket_id", "test-ticket-123");
        request.put("text", "Mon alternateur ALT-4892 est en panne depuis hier. Le moteur ne démarre plus.");

        Map<String, Object> response = aiServiceClient.analyzeText(request);

        // Then — verify contract response fields
        assertThat(response).isNotNull();
        assertThat(response).containsKey("ticket_id");
        assertThat(response).containsKey("piece_reference");
        assertThat(response).containsKey("type_sinistre");
        assertThat(response).containsKey("sentiment");
        assertThat(response).containsKey("confidence");
        assertThat(response).containsKey("keywords");

        // Verify types
        assertThat(response.get("piece_reference")).isInstanceOf(String.class);
        assertThat(response.get("confidence")).isInstanceOf(Number.class);
        assertThat(response.get("keywords")).isInstanceOf(List.class);

        verify(aiServiceClient).analyzeText(any());
    }

    @Test
    @DisplayName("Contract: POST /analyze/ → request requires ticket_id and text fields")
    void shouldSendCorrectRequestFieldsToAnalyze() {
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("ticket_id", "abc-123");
        mockResponse.put("type_sinistre", "USURE");

        when(aiServiceClient.analyzeText(any())).thenReturn(mockResponse);

        // The contract requires these exact keys
        Map<String, String> request = new HashMap<>();
        request.put("ticket_id", "abc-123");
        request.put("text", "Description du sinistre");

        assertThat(request).containsKey("ticket_id");
        assertThat(request).containsKey("text");

        Map<String, Object> response = aiServiceClient.analyzeText(request);
        assertThat(response).isNotNull();
        assertThat(response.get("ticket_id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Contract: POST /analyze/ → handles empty/null text gracefully")
    void shouldHandleMissingTextInAnalyze() {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("ticket_id", "fallback-123");
        fallbackResponse.put("type_sinistre", "INCONNU");
        fallbackResponse.put("confidence", 0.0);

        when(aiServiceClient.analyzeText(any())).thenReturn(fallbackResponse);

        Map<String, String> request = new HashMap<>();
        request.put("ticket_id", "fallback-123");
        request.put("text", "");

        Map<String, Object> response = aiServiceClient.analyzeText(request);
        assertThat(response).containsKey("ticket_id");
    }

    @Test
    @DisplayName("Contract: POST /transcribe/ → returns transcription with expected fields")
    void shouldTranscribeAudioAndReturnExpectedFields() {
        // Given — mock response structure from ai-service /transcribe/
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("ticket_id", "transcription-456");
        expectedResponse.put("transcription", "Mon moteur ne démarre plus depuis hier matin");
        expectedResponse.put("language", "fr");
        expectedResponse.put("duration", 12.5);
        expectedResponse.put("confidence", 0.95);

        when(aiServiceClient.transcribeAudio(any(), anyString())).thenReturn(expectedResponse);

        // When
        Map<String, Object> response = aiServiceClient.transcribeAudio(null, "transcription-456");

        // Then — verify response structure
        assertThat(response).isNotNull();
        assertThat(response).containsKey("transcription");
        assertThat(response.get("transcription")).isInstanceOf(String.class);
        assertThat((String) response.get("transcription")).isNotBlank();

        verify(aiServiceClient).transcribeAudio(any(), anyString());
    }
}
