package com.pfe.callmgmt.analytics.listener;

import com.pfe.callmgmt.analytics.event.*;
import com.pfe.callmgmt.analytics.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsListenerTest {

    // --- SonarCloud: extracted string constants ---
    private static final String STATUT_EN_COURS = "EN_COURS";
    private static final String TICKET_NUMERO = "T-20250001";

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsListener analyticsListener;

    @Test
    @DisplayName("onTicketCreated — incrémente les stats avec le canal")
    void onTicketCreated_incrementsStats() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setCanal("CALL");

        analyticsListener.onTicketCreated(event);

        verify(analyticsService).incrementTicketCreated("CALL");
    }

    @Test
    @DisplayName("onTicketCreated — gère les exceptions sans crash")
    void onTicketCreated_handlesException() {
        TicketCreatedEvent event = new TicketCreatedEvent();
        event.setCanal("WEB");
        doThrow(new RuntimeException("DB error")).when(analyticsService).incrementTicketCreated(any());

        analyticsListener.onTicketCreated(event);

        verify(analyticsService).incrementTicketCreated("WEB");
    }

    @Test
    @DisplayName("onTicketResolved — passe ticketCreatedAt au service")
    void onTicketResolved_incrementsStats() {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(2);
        TicketResolvedEvent event = new TicketResolvedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setTicketCreatedAt(createdAt);

        analyticsListener.onTicketResolved(event);

        verify(analyticsService).incrementTicketResolved(createdAt);
    }

    @Test
    @DisplayName("onTicketStatusChanged — transmet la transition au service")
    void onTicketStatusChanged_delegatesToService() {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent();
        event.setNumero(TICKET_NUMERO);
        event.setOldStatut("NOUVEAU");
        event.setNewStatut(STATUT_EN_COURS);

        analyticsListener.onTicketStatusChanged(event);

        verify(analyticsService).onTicketStatusChanged("NOUVEAU", STATUT_EN_COURS);
    }

    @Test
    @DisplayName("onTicketStatusChanged — gère les exceptions sans crash")
    void onTicketStatusChanged_handlesException() {
        TicketStatusChangedEvent event = new TicketStatusChangedEvent();
        event.setOldStatut(STATUT_EN_COURS);
        event.setNewStatut("RESOLU");
        doThrow(new RuntimeException("DB error")).when(analyticsService).onTicketStatusChanged(any(), any());

        analyticsListener.onTicketStatusChanged(event);

        verify(analyticsService).onTicketStatusChanged(STATUT_EN_COURS, "RESOLU");
    }

    @Test
    @DisplayName("onDecisionFinalized — FAVORABLE")
    void onDecisionFinalized_favorable() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setFinalDecision("FAVORABLE");

        analyticsListener.onDecisionFinalized(event);

        verify(analyticsService).incrementDecision("FAVORABLE");
    }

    @Test
    @DisplayName("onDecisionFinalized — DEFAVORABLE")
    void onDecisionFinalized_defavorable() {
        DecisionFinalizedEvent event = new DecisionFinalizedEvent();
        event.setTicketId(UUID.randomUUID());
        event.setFinalDecision("DEFAVORABLE");

        analyticsListener.onDecisionFinalized(event);

        verify(analyticsService).incrementDecision("DEFAVORABLE");
    }
}
