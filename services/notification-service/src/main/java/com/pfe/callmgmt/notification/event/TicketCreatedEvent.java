package com.pfe.callmgmt.notification.event;

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
    private String clientEmail;
    private String canal;
    private UUID agenceId;
    private UUID operateurId;
    private String operateurEmail;
}
