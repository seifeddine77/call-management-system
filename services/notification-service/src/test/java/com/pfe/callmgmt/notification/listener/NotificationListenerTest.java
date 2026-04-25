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
        event.setNumero("T-20250001");
        event.setClientNom("Client Test");
        event.setCanal("CALL");
        event.setOperateurEmail("operateur@test.com");

        notificationListener.onTicketCreated(event);

        verify(notificationService).sendEmail(
                eq("operateur@test.com"),
                contains("T-20250001"),
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
        event.setNumero("T-20250001");
        event.setClientNom("Client Test");
        event.setClientEmail("client@test.com");

        notificationListener.onTicketResolved(event);

        verify(notificationService).sendEmail(
                eq("client@test.com"),
                contains("T-20250001"),
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
        event.setClientNom("Client Test");
        event.setClientEmail("client@test.com");
        event.setFinalDecision("FAVORABLE");
        event.setMotif("Pièce sous garantie");

        notificationListener.onDecisionFinalized(event);

        verify(notificationService).sendEmail(
                eq("client@test.com"),
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
        event.setClientNom("Client Test");
        event.setClientEmail("client@test.com");
        event.setFinalDecision("DEFAVORABLE");
        event.setMotif("Hors garantie");

        notificationListener.onDecisionFinalized(event);

        verify(notificationService).sendEmail(
                eq("client@test.com"),
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
}
