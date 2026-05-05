package com.pfe.callmgmt.ticket.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@ConditionalOnBean(RabbitTemplate.class)
@RequiredArgsConstructor
public class TicketEventPublisher {

    private static final String TICKET_EXCHANGE = "ticket.exchange";

    private final RabbitTemplate rabbitTemplate;

    public void publishTicketCreated(TicketCreatedEvent event) {
        log.info("Publishing TicketCreatedEvent for ticket: {}", event.getNumero());
        rabbitTemplate.convertAndSend(TICKET_EXCHANGE, "ticket.created", event);
    }

    public void publishTicketResolved(TicketResolvedEvent event) {
        log.info("Publishing TicketResolvedEvent for ticket: {}", event.getNumero());
        rabbitTemplate.convertAndSend(TICKET_EXCHANGE, "ticket.resolved", event);
    }

    public void publishTicketStatusChanged(TicketStatusChangedEvent event) {
        log.info("Publishing TicketStatusChangedEvent for ticket: {} ({} → {})",
                event.getNumero(), event.getOldStatut(), event.getNewStatut());
        rabbitTemplate.convertAndSend(TICKET_EXCHANGE, "ticket.status-changed", event);
    }
}
