package com.pfe.callmgmt.analytics.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TICKET_CREATED_QUEUE = "ticket.created";
    public static final String TICKET_RESOLVED_QUEUE = "ticket.resolved";
    public static final String TICKET_STATUS_CHANGED_QUEUE = "ticket.status-changed";
    public static final String DECISION_FINALIZED_QUEUE = "decision.finalized";

    @Bean
    public Queue analyticsTicketCreatedQueue() {
        return new Queue(TICKET_CREATED_QUEUE, true);
    }

    @Bean
    public Queue analyticsTicketResolvedQueue() {
        return new Queue(TICKET_RESOLVED_QUEUE, true);
    }

    @Bean
    public Queue ticketStatusChangedQueue() {
        return new Queue(TICKET_STATUS_CHANGED_QUEUE, true);
    }

    @Bean
    public Queue analyticsDecisionFinalizedQueue() {
        return new Queue(DECISION_FINALIZED_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
