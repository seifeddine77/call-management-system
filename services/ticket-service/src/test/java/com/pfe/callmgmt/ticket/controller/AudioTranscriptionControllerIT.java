package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.repository.TicketActionRepository;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Module 3 — Audio Upload & Transcription.
 * Tests the full lifecycle: upload → transcription via AI service → NLP
 * analysis → storage.
 * AiServiceClient is mocked to test without the Python ai-service running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AudioTranscriptionControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @SuppressWarnings("unused")
        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private TicketRepository ticketRepository;

        @Autowired
        private TicketActionRepository actionRepository;

        @MockBean
        private AiServiceClient aiServiceClient;

        private static final UUID AGENCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID OPERATOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

        @BeforeEach
        void cleanup() {
                actionRepository.deleteAll();
                ticketRepository.deleteAll();
        }

        private Ticket createAndSaveTicket() {
                Ticket ticket = Ticket.builder()
                                .clientNom("Client Audio Test")
                                .clientTel("98222333")
                                .clientEmail("audio@test.com")
                                .canal(Ticket.Canal.CALL)
                                .statut(Ticket.Statut.EN_COURS)
                                .priorite((short) 2)
                                .agenceId(AGENCE_ID)
                                .build();
                return ticketRepository.save(ticket);
        }

        // ===========================================================
        // POST /api/tickets/{id}/audio — Upload audio + transcription
        // ===========================================================
        @Nested
        @DisplayName("POST /api/tickets/{id}/audio")
        class AudioUploadIT {

                @Test
                @DisplayName("200 — Upload audio + transcription réussie via AI service (mock)")
                void uploadAudio_success_withAiService() throws Exception {
                        // Given
                        Ticket ticket = createAndSaveTicket();
                        UUID ticketId = ticket.getId();

                        // Mock AI service: transcription response
                        when(aiServiceClient.transcribeAudio(any(), anyString()))
                                        .thenReturn(Map.of(
                                                        "text",
                                                        "Bonjour, mon alternateur est en panne depuis hier matin.",
                                                        "confidence", 0.87,
                                                        "duration_seconds", 5.2,
                                                        "file_path", "/audio/" + ticketId + "/recording.wav"));

                        // Mock AI service: NLP analysis response
                        when(aiServiceClient.analyzeText(any()))
                                        .thenReturn(Map.of(
                                                        "piece_reference", "alternateur",
                                                        "type_sinistre", "PANNE",
                                                        "sentiment", "NEUTRE",
                                                        "keywords", java.util.List.of("alternateur", "panne"),
                                                        "confidence", 0.9));

                        MockMultipartFile audioFile = new MockMultipartFile(
                                        "file",
                                        "call_recording.wav",
                                        "audio/wav",
                                        new byte[] { 0x00, 0x01, 0x02, 0x03 } // fake WAV content
                        );

                        // When
                        mockMvc.perform(multipart("/api/tickets/{id}/audio", ticketId)
                                        .file(audioFile)
                                        .header("X-User-Id", OPERATOR_ID)
                                        .header("X-User-Email", "operator@test.com"))
                                        // Then
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(ticketId.toString()))
                                        .andExpect(jsonPath("$.audioPath").isNotEmpty())
                                        .andExpect(jsonPath("$.transcription")
                                                        .value("Bonjour, mon alternateur est en panne depuis hier matin."));

                        // Verify ticket updated in DB
                        Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
                        assertThat(updated.getTranscription())
                                        .isEqualTo("Bonjour, mon alternateur est en panne depuis hier matin.");
                        assertThat(updated.getAudioPath()).isNotNull();

                        // Verify AI service was called: once for transcription, once for NLP
                        verify(aiServiceClient, times(1)).transcribeAudio(any(), eq(ticketId.toString()));
                        verify(aiServiceClient, times(1)).analyzeText(any());
                }

                @Test
                @DisplayName("200 — Upload réussi même si AI service est down (fallback: stocke audioPath uniquement)")
                void uploadAudio_aiServiceDown_fallbackToAudioPathOnly() throws Exception {
                        // Given
                        Ticket ticket = createAndSaveTicket();
                        UUID ticketId = ticket.getId();

                        // AI service is down — throws exception
                        when(aiServiceClient.transcribeAudio(any(), anyString()))
                                        .thenThrow(new RuntimeException("ai-service connection refused"));

                        MockMultipartFile audioFile = new MockMultipartFile(
                                        "file",
                                        "recording.wav",
                                        "audio/wav",
                                        new byte[] { 0x00, 0x01 });

                        // When — should NOT throw 500, fallback stores audioPath only
                        mockMvc.perform(multipart("/api/tickets/{id}/audio", ticketId)
                                        .file(audioFile)
                                        .header("X-User-Id", OPERATOR_ID))
                                        // Then — 200 with fallback audioPath stored
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(ticketId.toString()))
                                        .andExpect(jsonPath("$.audioPath").isNotEmpty());

                        // Verify audioPath persisted even without transcription
                        Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
                        assertThat(updated.getAudioPath()).isNotNull();
                        assertThat(updated.getTranscription()).isNull(); // no AI processing occurred
                }

                @Test
                @DisplayName("404 — Upload sur ticket inexistant retourne 404")
                void uploadAudio_ticketNotFound_returns404() throws Exception {
                        UUID fakeId = UUID.randomUUID();

                        MockMultipartFile audioFile = new MockMultipartFile(
                                        "file", "test.wav", "audio/wav", new byte[] { 0x00 });

                        mockMvc.perform(multipart("/api/tickets/{id}/audio", fakeId)
                                        .file(audioFile)
                                        .header("X-User-Id", OPERATOR_ID))
                                        .andExpect(status().isNotFound());
                }
        }

        // ===========================================================
        // GET /api/tickets/{id}/transcription
        // ===========================================================
        @Nested
        @DisplayName("GET /api/tickets/{id}/transcription")
        class GetTranscriptionIT {

                @Test
                @DisplayName("200 — Récupération transcription existante")
                void getTranscription_returnsData() throws Exception {
                        // Given: ticket with transcription
                        Ticket ticket = createAndSaveTicket();
                        ticket.setAudioPath("/audio/" + ticket.getId() + "/call.wav");
                        ticket.setTranscription("Mon moteur est en panne depuis 3 jours.");
                        ticket.setTranscriptionKeywords("[\"moteur\", \"panne\"]");
                        ticketRepository.save(ticket);

                        // When
                        mockMvc.perform(get("/api/tickets/{id}/transcription", ticket.getId())
                                        .header("X-User-Role", "ADMIN")
                                        .header("X-User-Id", OPERATOR_ID))
                                        // Then
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()))
                                        .andExpect(jsonPath("$.transcription")
                                                        .value("Mon moteur est en panne depuis 3 jours."))
                                        .andExpect(jsonPath("$.hasAudio").value(true))
                                        .andExpect(jsonPath("$.hasTranscription").value(true));
                }

                @Test
                @DisplayName("404 — Ticket sans transcription ni audio retourne 404")
                void getTranscription_noAudio_returns404() throws Exception {
                        // Given: ticket without any audio
                        Ticket ticket = createAndSaveTicket();

                        // When
                        mockMvc.perform(get("/api/tickets/{id}/transcription", ticket.getId())
                                        .header("X-User-Role", "ADMIN")
                                        .header("X-User-Id", OPERATOR_ID))
                                        // Then
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.message")
                                                        .value("Aucune transcription disponible pour ce ticket"));
                }

                @Test
                @DisplayName("404 — Transcription pour ticket inconnu retourne 404")
                void getTranscription_ticketNotFound_returns404() throws Exception {
                        UUID fakeId = UUID.randomUUID();

                        mockMvc.perform(get("/api/tickets/{id}/transcription", fakeId)
                                        .header("X-User-Role", "ADMIN")
                                        .header("X-User-Id", OPERATOR_ID))
                                        .andExpect(status().isNotFound());
                }
        }

        // ===========================================================
        // GET /api/tickets/{id} — TicketResponse includes audio fields
        // ===========================================================
        @Nested
        @DisplayName("GET /api/tickets/{id} — Champs audio dans TicketResponse")
        class TicketResponseAudioFieldsIT {

                @Test
                @DisplayName("200 — TicketResponse inclut audioPath, transcription, transcriptionKeywords")
                void getTicket_includesAudioFields() throws Exception {
                        // Given
                        Ticket ticket = createAndSaveTicket();
                        ticket.setAudioPath("/audio/" + ticket.getId() + "/call.wav");
                        ticket.setTranscription("L'alternateur ne fonctionne plus.");
                        ticket.setTranscriptionKeywords("[\"alternateur\"]");
                        ticketRepository.save(ticket);

                        // When
                        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                                        .header("X-User-Role", "ADMIN")
                                        .header("X-User-Id", OPERATOR_ID))
                                        // Then
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.audioPath").value(containsString("call.wav")))
                                        .andExpect(jsonPath("$.transcription")
                                                        .value("L'alternateur ne fonctionne plus."))
                                        .andExpect(jsonPath("$.transcriptionKeywords").isNotEmpty());
                }
        }
}
