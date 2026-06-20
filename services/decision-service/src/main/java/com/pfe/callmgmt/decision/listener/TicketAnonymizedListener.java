package com.pfe.callmgmt.decision.listener;

import com.pfe.callmgmt.decision.config.RabbitMQConfig;
import com.pfe.callmgmt.decision.event.TicketAnonymizedEvent;
import com.pfe.callmgmt.decision.service.DecisionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consommateur RGPD — à réception de {@code ticket.anonymized}, supprime la décision et les
 * courriers (lignes + fichiers PDF) du ticket concerné.
 *
 * <p>Aucune capture d'exception : un échec propage et déclenche le rejeu RabbitMQ (retry borné
 * configuré dans application.yml), garantissant l'effacement. {@code deleteByTicketId} est idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketAnonymizedListener {

    private final DecisionServiceImpl decisionService;

    @RabbitListener(queues = RabbitMQConfig.TICKET_ANONYMIZED_DECISION_QUEUE)
    public void onTicketAnonymized(TicketAnonymizedEvent event) {
        log.info("🔒 RGPD: effacement décision + courriers pour ticket {}", event.getTicketId());
        decisionService.deleteByTicketId(event.getTicketId());
    }
}
