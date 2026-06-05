package com.pfe.callmgmt.ticket.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketEventPublisherTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, java.time.Month.JUNE, 5, 12, 0, 0);

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TicketEventPublisher ticketEventPublisher;

    @Test
    @DisplayName("publishTicketCreated envoie sur ticket.exchange avec routing key ticket.created")
    void publishTicketCreated_sendsToExchange() {
        TicketCreatedEvent event = TicketCreatedEvent.builder()
                .ticketId(UUID.randomUUID())
                .numero("TK-001")
                .canal("CALL")
                .build();

        ticketEventPublisher.publishTicketCreated(event);

        verify(rabbitTemplate).convertAndSend("ticket.exchange", "ticket.created", event);
    }

    @Test
    @DisplayName("publishTicketResolved envoie sur ticket.exchange avec routing key ticket.resolved")
    void publishTicketResolved_sendsToExchange() {
        TicketResolvedEvent event = TicketResolvedEvent.builder()
                .ticketId(UUID.randomUUID())
                .numero("TK-002")
                .ticketCreatedAt(FIXED_NOW.minusHours(2))
                .build();

        ticketEventPublisher.publishTicketResolved(event);

        verify(rabbitTemplate).convertAndSend("ticket.exchange", "ticket.resolved", event);
    }

    @Test
    @DisplayName("publishTicketStatusChanged envoie sur ticket.exchange avec routing key ticket.status-changed")
    void publishTicketStatusChanged_sendsToExchange() {
        TicketStatusChangedEvent event = TicketStatusChangedEvent.builder()
                .ticketId(UUID.randomUUID())
                .numero("TK-003")
                .oldStatut("NOUVEAU")
                .newStatut("EN_COURS")
                .build();

        ticketEventPublisher.publishTicketStatusChanged(event);

        verify(rabbitTemplate).convertAndSend("ticket.exchange", "ticket.status-changed", event);
    }
}
