package com.pfe.callmgmt.ticket.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Événement émis quand un ticket est anonymisé (RGPD — droit à l'oubli).
 * Consommé par :
 *   - ticket-service (listener interne) → purge des données IA (audio + transcription) via REST,
 *   - decision-service (listener) → suppression de la décision et des courriers.
 * La livraison durable RabbitMQ garantit le rejeu si un consommateur est indisponible.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketAnonymizedEvent {
    private UUID ticketId;
}
