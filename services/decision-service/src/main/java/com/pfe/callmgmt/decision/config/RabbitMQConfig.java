package com.pfe.callmgmt.decision.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String DECISION_EXCHANGE = "decision.exchange";
    public static final String DECISION_FINALIZED_QUEUE = "decision.finalized";
    public static final String DECISION_FINALIZED_ROUTING_KEY = "decision.finalized";

    // RGPD — consommation de l'événement d'anonymisation émis par le ticket-service.
    public static final String TICKET_EXCHANGE = "ticket.exchange";
    public static final String TICKET_ANONYMIZED_ROUTING = "ticket.anonymized";
    public static final String TICKET_ANONYMIZED_DECISION_QUEUE = "ticket.anonymized.decision";

    @Bean
    public TopicExchange decisionExchange() {
        return new TopicExchange(DECISION_EXCHANGE);
    }

    // Déclaration idempotente de l'exchange du ticket-service (même nom/type) pour pouvoir
    // y lier notre queue de consommation, quel que soit l'ordre de démarrage des services.
    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(TICKET_EXCHANGE);
    }

    @Bean
    public Queue ticketAnonymizedDecisionQueue() {
        return QueueBuilder.durable(TICKET_ANONYMIZED_DECISION_QUEUE).build();
    }

    @Bean
    public Binding ticketAnonymizedDecisionBinding(Queue ticketAnonymizedDecisionQueue, TopicExchange ticketExchange) {
        return BindingBuilder.bind(ticketAnonymizedDecisionQueue).to(ticketExchange).with(TICKET_ANONYMIZED_ROUTING);
    }

    @Bean
    public Queue decisionFinalizedQueue() {
        return QueueBuilder.durable(DECISION_FINALIZED_QUEUE).build();
    }

    @Bean
    public Binding decisionFinalizedBinding(Queue decisionFinalizedQueue, TopicExchange decisionExchange) {
        return BindingBuilder.bind(decisionFinalizedQueue).to(decisionExchange).with(DECISION_FINALIZED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
