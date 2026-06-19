package com.pfe.callmgmt.ticket.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        // ── Publisher confirms (B) — ferme le trou « perte silencieuse à l'émission » ──
        // mandatory=true : un message routé vers aucune queue déclenche le returnsCallback
        // au lieu d'être abandonné en silence.
        template.setMandatory(true);

        // ConfirmCallback : le broker confirme (ack) ou rejette (nack) la réception.
        // Un nack ou un timeout signale une perte côté broker → loggué en ERROR.
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("❌ RabbitMQ NACK — message NON confirmé par le broker (perte probable). cause={}", cause);
            } else if (log.isDebugEnabled()) {
                log.debug("✅ RabbitMQ ACK — message confirmé par le broker.");
            }
        });

        // ReturnsCallback : message accepté par le broker mais non routable
        // (exchange/binding manquant) → loggué au lieu d'être perdu silencieusement.
        template.setReturnsCallback(returned -> log.error(
                "❌ RabbitMQ RETURN — message non routable (exchange='{}', routingKey='{}', replyCode={}, replyText='{}')",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));

        return template;
    }
}
