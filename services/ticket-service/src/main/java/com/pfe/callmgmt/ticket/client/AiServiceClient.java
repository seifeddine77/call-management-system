package com.pfe.callmgmt.ticket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Feign client for the AI service (Python FastAPI).
 * Used to forward audio files for transcription and text for NLP analysis.
 */
@FeignClient(name = "ai-service")
public interface AiServiceClient {

        @PostMapping(value = "/transcribe/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        Map<String, Object> transcribeAudio(
                        @RequestPart("file") MultipartFile file,
                        @RequestPart("ticket_id") String ticketId);

        @PostMapping(value = "/analyze/", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> analyzeText(@RequestBody Map<String, String> request);
}
