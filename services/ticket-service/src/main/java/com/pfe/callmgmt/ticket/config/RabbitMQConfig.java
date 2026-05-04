package com.pfe.callmgmt.ticket.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

    public static final String TICKET_EXCHANGE = "ticket.exchange";
    public static final String TICKET_CREATED_QUEUE = "ticket.created";
    public static final String TICKET_RESOLVED_QUEUE = "ticket.resolved";
    public static final String TICKET_STATUS_CHANGED_QUEUE = "ticket.status-changed";

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(TICKET_EXCHANGE);
    }

    @Bean
    public Queue ticketCreatedQueue() {
        return QueueBuilder.durable(TICKET_CREATED_QUEUE).build();
    }

    @Bean
    public Queue ticketResolvedQueue() {
        return QueueBuilder.durable(TICKET_RESOLVED_QUEUE).build();
    }

    @Bean
    public Queue ticketStatusChangedQueue() {
        return QueueBuilder.durable(TICKET_STATUS_CHANGED_QUEUE).build();
    }

    @Bean
    public Binding ticketCreatedBinding(Queue ticketCreatedQueue, TopicExchange ticketExchange) {
        // SonarCloud HIGH fix — use constant instead of duplicated string literal
        return BindingBuilder.bind(ticketCreatedQueue).to(ticketExchange).with(TICKET_CREATED_QUEUE);
    }

    @Bean
    public Binding ticketResolvedBinding(Queue ticketResolvedQueue, TopicExchange ticketExchange) {
        // SonarCloud HIGH fix — use constant instead of duplicated string literal
        return BindingBuilder.bind(ticketResolvedQueue).to(ticketExchange).with(TICKET_RESOLVED_QUEUE);
    }

    @Bean
    public Binding ticketStatusChangedBinding(Queue ticketStatusChangedQueue, TopicExchange ticketExchange) {
        // SonarCloud HIGH fix — use constant instead of duplicated string literal
        return BindingBuilder.bind(ticketStatusChangedQueue).to(ticketExchange).with(TICKET_STATUS_CHANGED_QUEUE);
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
