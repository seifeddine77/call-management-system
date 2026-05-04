package com.pfe.callmgmt.notification.listener;

import com.pfe.callmgmt.notification.event.*;
import com.pfe.callmgmt.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    // SonarCloud HIGH fix — extract duplicated string literals
    private static final String VAR_NUMERO = "numero";
    private static final String VAR_CLIENT_NOM = "clientNom";
    private static final String DECISION_FAVORABLE = "FAVORABLE";

    private final NotificationService notificationService;

    @RabbitListener(queues = "notification.user.created")
    public void onUserCreated(UserCreatedEvent event) {
        log.info("📨 Event reçu: user.created → {}", event.getEmail());
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", event.getPrenom() + " " + event.getNom());
        vars.put("role", event.getRole());
        vars.put("email", event.getEmail());

        notificationService.sendEmail(
                event.getEmail(),
                "Bienvenue sur Call Management",
                "emails/welcome",
                vars,
                "WELCOME");
    }

    @RabbitListener(queues = "notification.ticket.created")
    public void onTicketCreated(TicketCreatedEvent event) {
        log.info("📨 Event reçu: ticket.created → {} ({})", event.getNumero(), event.getCanal());

        // 1. Email à l'opérateur assigné (si assigné)
        if (event.getOperateurEmail() != null && !event.getOperateurEmail().isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put(VAR_NUMERO, event.getNumero());
            vars.put(VAR_CLIENT_NOM, event.getClientNom());
            vars.put("canal", event.getCanal());

            notificationService.sendEmail(
                    event.getOperateurEmail(),
                    "Nouveau ticket " + event.getNumero() + " — Action requise",
                    "emails/ticket-created",
                    vars,
                    "TICKET_CREATED");
        }

        // 2. Email de confirmation au client (si email fourni)
        if (event.getClientEmail() != null && !event.getClientEmail().isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put(VAR_NUMERO, event.getNumero());
            vars.put(VAR_CLIENT_NOM, event.getClientNom());
            vars.put("canal", event.getCanal());

            notificationService.sendEmail(
                    event.getClientEmail(),
                    "Votre demande " + event.getNumero() + " a été enregistrée",
                    "emails/ticket-created",
                    vars,
                    "TICKET_CREATED_CLIENT");
        }
    }

    @RabbitListener(queues = "notification.ticket.resolved")
    public void onTicketResolved(TicketResolvedEvent event) {
        log.info("📨 Event reçu: ticket.resolved → {}", event.getNumero());
        if (event.getClientEmail() != null && !event.getClientEmail().isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put(VAR_NUMERO, event.getNumero());
            vars.put(VAR_CLIENT_NOM, event.getClientNom());

            notificationService.sendEmail(
                    event.getClientEmail(),
                    "Votre demande " + event.getNumero() + " a été traitée",
                    "emails/ticket-resolved",
                    vars,
                    "TICKET_RESOLVED");
        }
    }

    @RabbitListener(queues = "notification.decision.finalized")
    public void onDecisionFinalized(DecisionFinalizedEvent event) {
        log.info("📨 Event reçu: decision.finalized → ticket={}, décision={}",
                event.getTicketId(), event.getFinalDecision());
        if (event.getClientEmail() != null && !event.getClientEmail().isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put(VAR_CLIENT_NOM, event.getClientNom() != null ? event.getClientNom() : "Client");
            vars.put("finalDecision", event.getFinalDecision());
            vars.put("motif", event.getMotif());
            vars.put("ticketId", event.getTicketId().toString().substring(0, 8).toUpperCase());

            boolean isFavorable = DECISION_FAVORABLE.equals(event.getFinalDecision());
            String templateName = isFavorable
                    ? "emails/decision-favorable"
                    : "emails/decision-defavorable";

            String subject = isFavorable
                    ? "✅ Votre demande de garantie est acceptée — Réf. " + vars.get("ticketId")
                    : "Votre demande de garantie — Réf. " + vars.get("ticketId");

            String type = isFavorable
                    ? "DECISION_FAVORABLE"
                    : "DECISION_DEFAVORABLE";

            // Read PDF attachment if letterFilePath is provided
            byte[] pdfAttachment = null;
            String attachmentName = null;
            if (event.getLetterFilePath() != null && !event.getLetterFilePath().isBlank()) {
                try {
                    java.nio.file.Path pdfPath = java.nio.file.Paths.get(event.getLetterFilePath());
                    if (java.nio.file.Files.exists(pdfPath)) {
                        pdfAttachment = java.nio.file.Files.readAllBytes(pdfPath);
                        attachmentName = "courrier_decision_" + vars.get("ticketId") + ".pdf";
                        log.info("📎 PDF trouvé: {} ({} bytes)", pdfPath, pdfAttachment.length);
                    } else {
                        log.warn("⚠️ PDF introuvable: {}", event.getLetterFilePath());
                    }
                } catch (Exception e) {
                    log.error("❌ Erreur lecture PDF: {}", e.getMessage());
                }
            }

            notificationService.sendEmail(event.getClientEmail(), subject, templateName,
                    vars, pdfAttachment, attachmentName, type);
        }
    }
}
