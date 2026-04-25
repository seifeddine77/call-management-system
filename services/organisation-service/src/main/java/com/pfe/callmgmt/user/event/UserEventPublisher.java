package com.pfe.callmgmt.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RabbitTemplate.class)
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserCreated(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent for user: {}", event.getEmail());
        rabbitTemplate.convertAndSend("user.exchange", "user.created", event);
    }

    public void publishAgenceCreated(AgenceCreatedEvent event) {
        log.info("📢 Publishing AgenceCreatedEvent for agence: {} (tel: {})", event.getNom(), event.getTelephone());
        rabbitTemplate.convertAndSend("user.exchange", "agence.created", event);
    }
}
