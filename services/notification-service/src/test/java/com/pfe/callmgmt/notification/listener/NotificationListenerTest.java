package com.pfe.callmgmt.notification.listener;

import com.pfe.callmgmt.notification.event.*;
import com.pfe.callmgmt.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    // --- SonarCloud: extracted string constants ---
    private static final String CLIENT_NOM = "Client Test";
    private static final String TICKET_NUMERO = "T-20250001";
    private static final String CLIENT_EMAIL = "client@test.com";

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationListener notificationListener;

    @Test
    @DisplayName("onUserCreated — envoie email de bienvenue")
    void onUserCreated_sendsWelcomeEmail() {
        UserCreatedEvent event = new UserCreatedEvent();
        event.setEmail("ahmed@test.com");
        event.setNom("Ahmed Ben Ali"); event.setPrenom("");
        event.setRole("OPERATOR");

        notificationListener.onUserCreated(event);

        verify(notificationService).sendEmail(
                eq("ahmed@test.com"),
                eq("Bienvenue sur Call Management"),
                eq("emails/welcome"),
                anyMap(),
                eq("WELCOME"));
    }

    @Test
    @DisplayName("onTicketCreated — envoie email à l'opérateur")
    void onTicketCreated_sendsEmailToOperator() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setClientNom(CLIENT_NOM);
        event.setCanal("CALL");
        event.setOperateurEmail("operateur@test.com");

        notificationListener.onTicketCreated(event);

        verify(notificationService).sendEmail(
                eq("operateur@test.com"),
                contains(TICKET_NUMERO),
                eq("emails/ticket-created"),
                anyMap(),
                eq("TICKET_CREATED"));
    }

    @Test
    @DisplayName("onTicketCreated — pas d'email si operateurEmail null")
    void onTicketCreated_noEmailIfOperateurNull() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setNumero("T-20250002");
        event.setOperateurEmail(null);

        notificationListener.onTicketCreated(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("onTicketResolved — envoie email au client")
    void onTicketResolved_sendsEmailToClient() {
        TicketResolvedEvent event = new TicketResolvedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setClientNom(CLIENT_NOM);
        event.setClientEmail(CLIENT_EMAIL);

        notificationListener.onTicketResolved(event);

        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL),
                contains(TICKET_NUMERO),
                eq("emails/ticket-resolved"),
                anyMap(),
                eq("TICKET_RESOLVED"));
    }

    @Test
    @DisplayName("onTicketResolved — pas d'email si clientEmail null")
    void onTicketResolved_noEmailIfClientNull() {
        TicketResolvedEvent event = new TicketResolvedEvent();
        event.setClientEmail(null);

        notificationListener.onTicketResolved(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("onDecisionFinalized — FAVORABLE → template decision-favorable")
    void onDecisionFinalized_favorable() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientNom(CLIENT_NOM);
        event.setClientEmail(CLIENT_EMAIL);
        event.setFinalDecision("FAVORABLE");
        event.setMotif("Pièce sous garantie");

        notificationListener.onDecisionFinalized(event);

        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL),
                contains("acceptée"),
                eq("emails/decision-favorable"),
                anyMap(),
                isNull(),
                isNull(),
                eq("DECISION_FAVORABLE"));
    }

    @Test
    @DisplayName("onDecisionFinalized — DEFAVORABLE → template decision-defavorable")
    void onDecisionFinalized_defavorable() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientNom(CLIENT_NOM);
        event.setClientEmail(CLIENT_EMAIL);
        event.setFinalDecision("DEFAVORABLE");
        event.setMotif("Hors garantie");

        notificationListener.onDecisionFinalized(event);

        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL),
                contains("Réf."),
                eq("emails/decision-defavorable"),
                anyMap(),
                isNull(),
                isNull(),
                eq("DECISION_DEFAVORABLE"));
    }

    @Test
    @DisplayName("onDecisionFinalized — pas d'email si clientEmail null")
    void onDecisionFinalized_noEmailIfClientNull() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientEmail(null);
        event.setFinalDecision("FAVORABLE");

        notificationListener.onDecisionFinalized(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("onDecisionFinalized — clientEmail vide → aucune action")
    void onDecisionFinalized_noEmailIfClientEmailBlank() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientEmail("   ");
        event.setFinalDecision("FAVORABLE");

        notificationListener.onDecisionFinalized(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("onDecisionFinalized — clientNom null → utilise 'Client' par défaut")
    void onDecisionFinalized_nullClientNom_usesDefault() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientEmail(CLIENT_EMAIL);
        event.setClientNom(null); // doit utiliser "Client"
        event.setFinalDecision("DEFAVORABLE");
        event.setMotif("Hors garantie");

        notificationListener.onDecisionFinalized(event);

        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL),
                anyString(),
                eq("emails/decision-defavorable"),
                argThat(vars -> "Client".equals(vars.get("clientNom"))),
                isNull(),
                isNull(),
                eq("DECISION_DEFAVORABLE"));
    }

    @Test
    @DisplayName("onTicketCreated — opérateurEmail ET clientEmail → deux emails envoyés")
    void onTicketCreated_sendsEmailToBothOperatorAndClient() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setClientNom(CLIENT_NOM);
        event.setCanal("EMAIL");
        event.setOperateurEmail("operateur@test.com");
        event.setClientEmail(CLIENT_EMAIL);

        notificationListener.onTicketCreated(event);

        verify(notificationService).sendEmail(
                eq("operateur@test.com"), anyString(), eq("emails/ticket-created"),
                anyMap(), eq("TICKET_CREATED"));
        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL), anyString(), eq("emails/ticket-created"),
                anyMap(), eq("TICKET_CREATED_CLIENT"));
    }

    @Test
    @DisplayName("onTicketCreated — operateurEmail vide → pas d'email opérateur")
    void onTicketCreated_blankOperateurEmail_noOperatorEmail() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setOperateurEmail("   "); // blank
        event.setClientEmail(CLIENT_EMAIL);
        event.setCanal("WEB");

        notificationListener.onTicketCreated(event);

        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL), anyString(), eq("emails/ticket-created"),
                anyMap(), eq("TICKET_CREATED_CLIENT"));
        verify(notificationService, never()).sendEmail(
                eq("   "), anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    @DisplayName("onDecisionFinalized — letterFilePath inexistant → PDF null (pas d'exception)")
    void onDecisionFinalized_nonExistentPdf_sendsWithoutAttachment() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setClientEmail(CLIENT_EMAIL);
        event.setClientNom(CLIENT_NOM);
        event.setFinalDecision("FAVORABLE");
        event.setMotif("OK");
        event.setLetterFilePath("/tmp/nonexistent_pdf_file_xyz.pdf");

        notificationListener.onDecisionFinalized(event);

        // PDF does not exist → pdfAttachment=null, attachmentName=null
        verify(notificationService).sendEmail(
                eq(CLIENT_EMAIL), anyString(), eq("emails/decision-favorable"),
                anyMap(), isNull(), isNull(), eq("DECISION_FAVORABLE"));
    }
}
