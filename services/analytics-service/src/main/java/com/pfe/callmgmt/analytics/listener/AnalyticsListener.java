package com.pfe.callmgmt.analytics.listener;

import com.pfe.callmgmt.analytics.event.*;
import com.pfe.callmgmt.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsListener {

    private final AnalyticsService analyticsService;

    @RabbitListener(queues = "ticket.created")
    public void onTicketCreated(TicketCreatedEvent event) {
        log.info("📊 Event reçu: ticket.created → {} ({})", event.getNumero(), event.getCanal());
        try {
            analyticsService.incrementTicketCreated(event.getCanal());
        } catch (Exception e) {
            log.error("❌ Erreur traitement ticket.created: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = "ticket.resolved")
    public void onTicketResolved(TicketResolvedEvent event) {
        log.info("📊 Event reçu: ticket.resolved → {}", event.getNumero());
        try {
            analyticsService.incrementTicketResolved(event.getTicketCreatedAt());
        } catch (Exception e) {
            log.error("❌ Erreur traitement ticket.resolved: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = "ticket.status-changed")
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        log.info("📊 Event reçu: ticket.status-changed → {} ({} → {})",
                event.getNumero(), event.getOldStatut(), event.getNewStatut());
        try {
            analyticsService.onTicketStatusChanged(event.getOldStatut(), event.getNewStatut());
        } catch (Exception e) {
            log.error("❌ Erreur traitement ticket.status-changed: {}", e.getMessage());
        }
    }

    @RabbitListener(queues = "decision.finalized")
    public void onDecisionFinalized(DecisionFinalizedEvent event) {
        log.info("📊 Event reçu: decision.finalized → ticket={}, décision={}",
                event.getTicketId(), event.getFinalDecision());
        try {
            analyticsService.incrementDecision(event.getFinalDecision());
        } catch (Exception e) {
            log.error("❌ Erreur traitement decision.finalized: {}", e.getMessage());
        }
    }
}
