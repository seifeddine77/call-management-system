package com.pfe.callmgmt.user.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private UserEventPublisher userEventPublisher;

    @Test
    @DisplayName("publishUserCreated envoie sur user.exchange avec routing key user.created")
    void publishUserCreated_sendsToExchange() {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(UUID.randomUUID())
                .email("newuser@test.com")
                .nom("Dupont")
                .prenom("Jean")
                .role("OPERATEUR")
                .build();

        userEventPublisher.publishUserCreated(event);

        verify(rabbitTemplate).convertAndSend("user.exchange", "user.created", event);
    }

    @Test
    @DisplayName("publishAgenceCreated envoie sur user.exchange avec routing key agence.created")
    void publishAgenceCreated_sendsToExchange() {
        AgenceCreatedEvent event = AgenceCreatedEvent.builder()
                .agenceId(UUID.randomUUID().toString())
                .nom("Agence Paris")
                .telephone("0145678900")
                .build();

        userEventPublisher.publishAgenceCreated(event);

        verify(rabbitTemplate).convertAndSend("user.exchange", "agence.created", event);
    }
}
