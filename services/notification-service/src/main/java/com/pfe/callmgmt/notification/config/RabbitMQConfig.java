package com.pfe.callmgmt.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pfe.callmgmt.notification.event.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue userCreatedQueue() {
        return new Queue("notification.user.created", true);
    }

    @Bean
    public Queue ticketCreatedQueue() {
        return new Queue("notification.ticket.created", true);
    }

    @Bean
    public Queue ticketResolvedQueue() {
        return new Queue("notification.ticket.resolved", true);
    }

    @Bean
    public Queue decisionFinalizedQueue() {
        return new Queue("notification.decision.finalized", true);
    }

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange("user.exchange");
    }

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange("ticket.exchange");
    }

    @Bean
    public TopicExchange decisionExchange() {
        return new TopicExchange("decision.exchange");
    }

    @Bean
    public Binding bindUserCreated(Queue userCreatedQueue, TopicExchange userExchange) {
        return BindingBuilder.bind(userCreatedQueue).to(userExchange).with("user.created");
    }

    @Bean
    public Binding bindTicketCreated(Queue ticketCreatedQueue, TopicExchange ticketExchange) {
        return BindingBuilder.bind(ticketCreatedQueue).to(ticketExchange).with("ticket.created");
    }

    @Bean
    public Binding bindTicketResolved(Queue ticketResolvedQueue, TopicExchange ticketExchange) {
        return BindingBuilder.bind(ticketResolvedQueue).to(ticketExchange).with("ticket.resolved");
    }

    @Bean
    public Binding bindDecisionFinalized(Queue decisionFinalizedQueue, TopicExchange decisionExchange) {
        return BindingBuilder.bind(decisionFinalizedQueue).to(decisionExchange).with("decision.finalized");
    }

    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("*");
        // Map publisher FQCNs → local notification-service event classes
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("com.pfe.callmgmt.ticket.event.TicketCreatedEvent", TicketCreatedEvent.class);
        idClassMapping.put("com.pfe.callmgmt.ticket.event.TicketResolvedEvent", TicketResolvedEvent.class);
        idClassMapping.put("com.pfe.callmgmt.user.event.UserCreatedEvent", UserCreatedEvent.class);
        idClassMapping.put("com.pfe.callmgmt.decision.event.DecisionFinalizedEvent", DecisionFinalizedEvent.class);
        classMapper.setIdClassMapping(idClassMapping);
        return classMapper;
    }

    @Bean
    public MessageConverter jsonMessageConverter(DefaultClassMapper classMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }
}
