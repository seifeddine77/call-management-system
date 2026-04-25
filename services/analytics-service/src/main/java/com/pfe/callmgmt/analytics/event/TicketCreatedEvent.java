package com.pfe.callmgmt.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreatedEvent {
    private UUID ticketId;
    private String numero;
    private String clientNom;
    private String canal; // CALL, EMAIL, WEB
    private UUID agenceId;
    private UUID operateurId;
}
