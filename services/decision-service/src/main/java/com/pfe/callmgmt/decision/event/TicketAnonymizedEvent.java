package com.pfe.callmgmt.decision.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Miroir de l'événement publié par le ticket-service quand un ticket est anonymisé (RGPD).
 * Désérialisé depuis la queue {@code ticket.anonymized.decision} ; les noms de champs
 * doivent correspondre à ceux du producteur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketAnonymizedEvent {
    private UUID ticketId;
}
