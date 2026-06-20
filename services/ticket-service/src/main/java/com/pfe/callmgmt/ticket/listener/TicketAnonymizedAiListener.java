package com.pfe.callmgmt.ticket.listener;

import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.config.RabbitMQConfig;
import com.pfe.callmgmt.ticket.event.TicketAnonymizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Consommateur RGPD — l'ai-service (Python) n'ayant pas de client AMQP, ce listener Java
 * fait le pont : à réception de {@code ticket.anonymized}, il appelle l'endpoint REST de
 * purge de l'ai-service (audio chiffré + transcriptions).
 *
 * <p>En cas d'échec (ai-service indisponible), l'exception est propagée : RabbitMQ rejoue
 * le message (retry borné configuré dans application.yml), ce qui garantit l'effacement
 * dès que l'ai-service redevient disponible. La purge est idempotente.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class TicketAnonymizedAiListener {

    private final AiServiceClient aiServiceClient;

    @RabbitListener(queues = RabbitMQConfig.TICKET_ANONYMIZED_AI_QUEUE)
    public void onTicketAnonymized(TicketAnonymizedEvent event) {
        String ticketId = String.valueOf(event.getTicketId());
        log.info("🔒 RGPD: effacement des données IA pour ticket {}", ticketId);
        // Pas de try/catch : une exception déclenche le rejeu RabbitMQ (garantie d'effacement).
        Object result = aiServiceClient.eraseTicketData(ticketId);
        log.info("🔒 RGPD: données IA effacées pour ticket {} — {}", ticketId, result);
    }
}
