package com.pfe.callmgmt.ticket.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreatedEvent implements Serializable {
    private UUID ticketId;
    private String numero;
    private String clientNom;
    private String clientEmail;
    private String canal;
    private UUID agenceId;
    private UUID operateurId;
}
