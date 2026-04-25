package com.pfe.callmgmt.notification.service;

import com.pfe.callmgmt.notification.entity.NotificationLog;
import com.pfe.callmgmt.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;
    @Mock private NotificationLogRepository logRepository;
    @Mock private MimeMessage mimeMessage;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "fromAddress", "test@callmgmt.com");
    }

    @Nested
    @DisplayName("sendEmail")
    class SendEmail {

        @Test
        @DisplayName("Envoi réussi — sauvegarde status SENT")
        void sendEmail_success_savesSENT() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any(IContext.class)))
                    .thenReturn("<html>Hello</html>");
            when(logRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> vars = Map.of("fullName", "Ahmed Ben Ali", "role", "OPERATOR");

            notificationService.sendEmail(
                    "ahmed@test.com", "Bienvenue", "emails/welcome", vars, "WELCOME");

            verify(mailSender).send(mimeMessage);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());

            NotificationLog saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("SENT");
            assertThat(saved.getRecipient()).isEqualTo("ahmed@test.com");
            assertThat(saved.getSubject()).isEqualTo("Bienvenue");
            assertThat(saved.getType()).isEqualTo("WELCOME");
            assertThat(saved.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("Échec envoi — sauvegarde status FAILED avec errorMessage")
        void sendEmail_failure_savesFAILED() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any(IContext.class)))
                    .thenReturn("<html>Hello</html>");
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));
            when(logRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.sendEmail(
                    "fail@test.com", "Test", "emails/welcome", Map.of(), "WELCOME");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());

            NotificationLog saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("FAILED");
            assertThat(saved.getErrorMessage()).contains("SMTP connection refused");
        }

        @Test
        @DisplayName("Template Thymeleaf bien appelé avec les variables")
        void sendEmail_callsThymeleafWithCorrectVars() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any(IContext.class)))
                    .thenReturn("<html>Content</html>");
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> vars = Map.of("numero", "T-20250001", "clientNom", "Client Test");

            notificationService.sendEmail(
                    "op@test.com", "Nouveau ticket", "emails/ticket-created", vars, "TICKET_CREATED");

            verify(templateEngine).process(eq("emails/ticket-created"), any(IContext.class));
        }

        @Test
        @DisplayName("Envoi avec pièce jointe PDF — sauvegarde status SENT")
        void sendEmail_withAttachment_savesSENT() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any(IContext.class)))
                    .thenReturn("<html>Decision</html>");
            when(logRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> vars = Map.of("clientNom", "Client", "motif", "Sous garantie");
            byte[] pdfData = "fake-pdf-content".getBytes();

            notificationService.sendEmail(
                    "client@test.com", "Décision favorable", "emails/decision-favorable",
                    vars, pdfData, "courrier_ABCD1234.pdf", "DECISION_FAVORABLE");

            verify(mailSender).send(mimeMessage);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        }
    }

    @Nested
    @DisplayName("retry")
    class Retry {

        @Test
        @DisplayName("Retry réussi — met à jour status à SENT")
        void retry_success() {
            NotificationLog failedLog = NotificationLog.builder()
                    .id(UUID.randomUUID())
                    .type("WELCOME")
                    .recipient("user@test.com")
                    .subject("Bienvenue")
                    .status("FAILED")
                    .attempts((short) 1)
                    .errorMessage("SMTP error")
                    .metadata(Map.of("fullName", "Test User"))
                    .build();

            when(logRepository.findById(failedLog.getId())).thenReturn(Optional.of(failedLog));
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any(IContext.class)))
                    .thenReturn("<html>Retry</html>");
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            NotificationLog result = notificationService.retry(failedLog.getId());

            assertThat(result.getStatus()).isEqualTo("SENT");
            assertThat(result.getAttempts()).isEqualTo((short) 2);
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Retry sur notification non-FAILED — lève IllegalStateException")
        void retry_notFailed_throwsException() {
            NotificationLog sentLog = NotificationLog.builder()
                    .id(UUID.randomUUID())
                    .status("SENT")
                    .build();

            when(logRepository.findById(sentLog.getId())).thenReturn(Optional.of(sentLog));

            assertThatThrownBy(() -> notificationService.retry(sentLog.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAILED");
        }

        @Test
        @DisplayName("Retry sur notification inexistante — lève NoSuchElementException")
        void retry_notFound_throwsException() {
            UUID fakeId = UUID.randomUUID();
            when(logRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.retry(fakeId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
