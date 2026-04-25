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

    @Bean
    public TopicExchange decisionExchange() {
        return new TopicExchange(DECISION_EXCHANGE);
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
