package com.pfe.callmgmt.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.notification.entity.NotificationLog;
import com.pfe.callmgmt.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration — Spring Boot context réel + MockMvc.
 * Teste le flux complet : Controller → Service → Repository (H2).
 * JavaMailSender est mocké pour éviter l'envoi réel d'emails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @SuppressWarnings("unused")
        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private NotificationLogRepository logRepository;

        @MockBean
        private JavaMailSender mailSender;

        @MockBean
        private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

        @MockBean
        private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

        @BeforeEach
        void setUp() {
                logRepository.deleteAll();
        }

        @Test
        @DisplayName("GET /health — retourne 200 OK")
        void health_returns200() throws Exception {
                mockMvc.perform(get("/api/notifications/health"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("OK"));
        }

        @Test
        @DisplayName("GET /logs — vide au départ → page vide")
        void getLogs_empty_returnsEmptyPage() throws Exception {
                mockMvc.perform(get("/api/notifications/logs")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0))
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("GET /logs — retourne les logs après insertion")
        void getLogs_withData_returnsLogs() throws Exception {
                // Insert test data
                logRepository.save(NotificationLog.builder()
                                .type("WELCOME").recipient("user1@test.com").subject("Bienvenue")
                                .status("SENT").attempts((short) 1).sentAt(LocalDateTime.now())
                                .metadata(Map.of("fullName", "User 1")).build());

                logRepository.save(NotificationLog.builder()
                                .type("TICKET_CREATED").recipient("op@test.com").subject("Nouveau ticket")
                                .status("FAILED").attempts((short) 1).errorMessage("SMTP error")
                                .metadata(Map.of("numero", "T-001")).build());

                mockMvc.perform(get("/api/notifications/logs")
                                .param("page", "0").param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(2))
                                .andExpect(jsonPath("$.content", hasSize(2)));
        }

        @Test
        @DisplayName("GET /logs?status=FAILED — filtre uniquement FAILED")
        void getLogs_filterFailed_returnsOnlyFailed() throws Exception {
                logRepository.save(NotificationLog.builder()
                                .type("WELCOME").recipient("ok@test.com").subject("Bienvenue")
                                .status("SENT").attempts((short) 1).build());

                logRepository.save(NotificationLog.builder()
                                .type("TICKET_CREATED").recipient("fail@test.com").subject("Ticket")
                                .status("FAILED").attempts((short) 1).errorMessage("Connection refused").build());

                logRepository.save(NotificationLog.builder()
                                .type("DECISION_FAVORABLE").recipient("fail2@test.com").subject("Décision")
                                .status("FAILED").attempts((short) 2).errorMessage("Timeout").build());

                mockMvc.perform(get("/api/notifications/logs")
                                .param("status", "FAILED")
                                .param("page", "0").param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(2))
                                .andExpect(jsonPath("$.content[*].status", everyItem(is("FAILED"))));
        }

        @Test
        @DisplayName("GET /logs?status=SENT — filtre uniquement SENT")
        void getLogs_filterSent_returnsOnlySent() throws Exception {
                logRepository.save(NotificationLog.builder()
                                .type("WELCOME").recipient("ok@test.com").subject("Bienvenue")
                                .status("SENT").attempts((short) 1).sentAt(LocalDateTime.now()).build());

                logRepository.save(NotificationLog.builder()
                                .type("TICKET_CREATED").recipient("fail@test.com").subject("Ticket")
                                .status("FAILED").attempts((short) 1).build());

                mockMvc.perform(get("/api/notifications/logs")
                                .param("status", "SENT")
                                .param("page", "0").param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.content[0].status").value("SENT"));
        }

        @Test
        @DisplayName("GET /logs — pagination fonctionne (page 0 size 1)")
        void getLogs_pagination_works() throws Exception {
                for (int i = 0; i < 5; i++) {
                        logRepository.save(NotificationLog.builder()
                                        .type("WELCOME").recipient("user" + i + "@test.com")
                                        .subject("Test " + i).status("SENT").attempts((short) 1).build());
                }

                mockMvc.perform(get("/api/notifications/logs")
                                .param("page", "0").param("size", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(5))
                                .andExpect(jsonPath("$.totalPages").value(3))
                                .andExpect(jsonPath("$.content", hasSize(2)));

                mockMvc.perform(get("/api/notifications/logs")
                                .param("page", "2").param("size", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("POST /retry/{id} — retry d'une notification FAILED")
        void retry_failedNotification_retriesAndReturns200() throws Exception {
                NotificationLog failedLog = logRepository.save(NotificationLog.builder()
                                .type("WELCOME").recipient("fail@test.com").subject("Bienvenue")
                                .status("FAILED").attempts((short) 1).errorMessage("SMTP error")
                                .metadata(Map.of("fullName", "Test User")).build());

                // Le retry va échouer car mailSender est mocké (null MimeMessage),
                // mais il doit incrémenter attempts
                mockMvc.perform(post("/api/notifications/retry/" + failedLog.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(failedLog.getId().toString()))
                                .andExpect(jsonPath("$.attempts").value(greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("POST /retry/{id} — notification inexistante → exception propagée")
        void retry_notFound_throwsException() {
                UUID fakeId = UUID.randomUUID();
                assertThrows(Exception.class, () -> mockMvc.perform(post("/api/notifications/retry/" + fakeId))
                                .andReturn());
        }

        @Test
        @DisplayName("Flux complet : insert log → GET filtered → retry → verify attempts")
        void fullFlow_insertFilterRetry() throws Exception {
                // 1. Insert SENT + FAILED
                logRepository.save(NotificationLog.builder()
                                .type("WELCOME").recipient("ok@test.com").subject("OK")
                                .status("SENT").attempts((short) 1).build());

                NotificationLog failed = logRepository.save(NotificationLog.builder()
                                .type("TICKET_RESOLVED").recipient("fail@test.com").subject("Résolu")
                                .status("FAILED").attempts((short) 1).errorMessage("Connection refused")
                                .metadata(Map.of("numero", "T-42", "clientNom", "Client")).build());

                // 2. GET all → 2 logs
                mockMvc.perform(get("/api/notifications/logs"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(2));

                // 3. GET FAILED → 1 log
                mockMvc.perform(get("/api/notifications/logs").param("status", "FAILED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.content[0].type").value("TICKET_RESOLVED"));

                // 4. Retry the failed notification
                mockMvc.perform(post("/api/notifications/retry/" + failed.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.attempts").value(greaterThanOrEqualTo(2)));
        }
}
