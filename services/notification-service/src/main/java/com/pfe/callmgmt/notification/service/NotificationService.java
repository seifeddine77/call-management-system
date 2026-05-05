package com.pfe.callmgmt.notification.service;

import com.pfe.callmgmt.notification.entity.NotificationLog;
import com.pfe.callmgmt.notification.repository.NotificationLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final String STATUS_FAILED = "FAILED";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationLogRepository logRepository;

    @Value("${notification.from-address:noreply@callmgmt.com}")
    private String fromAddress;

    /**
     * Send an email notification using a Thymeleaf template (sans pièce jointe).
     */
    public void sendEmail(String to, String subject, String templateName,
            Map<String, Object> variables, String type) {
        sendEmail(to, subject, templateName, variables, null, null, type);
    }

    /**
     * Send an email notification using a Thymeleaf template.
     * Fire-and-forget: exceptions are logged but not rethrown.
     * Supports optional PDF attachment (byte[] attachment).
     */
    public void sendEmail(String to, String subject, String templateName,
            Map<String, Object> variables, byte[] attachment, String attachmentName,
            String type) {
        NotificationLog notifLog = NotificationLog.builder()
                .type(type)
                .recipient(to)
                .subject(subject)
                .status("PENDING")
                .attempts((short) 0)
                .metadata(variables)
                .build();

        try {
            // Render HTML template
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            // Build MIME message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            // Add PDF attachment if provided
            if (attachment != null && attachmentName != null) {
                helper.addAttachment(attachmentName,
                        new org.springframework.core.io.ByteArrayResource(attachment),
                        "application/pdf");
                log.info("📎 Pièce jointe ajoutée: {} ({} bytes)", attachmentName, attachment.length);
            }

            mailSender.send(message);

            notifLog.setStatus("SENT");
            notifLog.setSentAt(LocalDateTime.now());
            notifLog.setAttempts((short) 1);
            log.info("📧 Email envoyé → {} | Sujet: {}", to, subject);

        } catch (MessagingException e) {
            notifLog.setStatus(STATUS_FAILED);
            notifLog.setErrorMessage(e.getMessage());
            notifLog.setAttempts((short) 1);
            log.error("❌ Échec envoi email → {} | Sujet: {} | Erreur: {}", to, subject, e.getMessage());
        } catch (Exception e) {
            notifLog.setStatus(STATUS_FAILED);
            notifLog.setErrorMessage(e.getMessage());
            notifLog.setAttempts((short) 1);
            log.error("❌ Erreur inattendue envoi email → {} | {}", to, e.getMessage());
        }

        logRepository.save(notifLog);
    }

    /**
     * Retry a failed notification by ID.
     */
    public NotificationLog retry(java.util.UUID id) {
        NotificationLog notifLog = logRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Notification introuvable : " + id));

        if (!STATUS_FAILED.equals(notifLog.getStatus())) {
            throw new IllegalStateException("Seules les notifications FAILED peuvent être relancées");
        }

        try {
            Context context = new Context();
            if (notifLog.getMetadata() != null) {
                context.setVariables(notifLog.getMetadata());
            }

            String templateName = switch (notifLog.getType()) {
                case "WELCOME" -> "emails/welcome";
                case "TICKET_CREATED" -> "emails/ticket-created";
                case "TICKET_RESOLVED" -> "emails/ticket-resolved";
                case "DECISION_FAVORABLE" -> "emails/decision-favorable";
                case "DECISION_DEFAVORABLE" -> "emails/decision-defavorable";
                default -> "emails/generic";
            };

            String htmlContent = templateEngine.process(templateName, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(notifLog.getRecipient());
            helper.setSubject(notifLog.getSubject());
            helper.setText(htmlContent, true);
            mailSender.send(message);

            notifLog.setStatus("SENT");
            notifLog.setSentAt(LocalDateTime.now());
            notifLog.setAttempts((short) (notifLog.getAttempts() + 1));
            notifLog.setErrorMessage(null);
            log.info("🔄 Email relancé avec succès → {}", notifLog.getRecipient());

        } catch (Exception e) {
            notifLog.setAttempts((short) (notifLog.getAttempts() + 1));
            notifLog.setErrorMessage(e.getMessage());
            log.error("❌ Retry échoué → {} | {}", notifLog.getRecipient(), e.getMessage());
        }

        return logRepository.save(notifLog);
    }
}
