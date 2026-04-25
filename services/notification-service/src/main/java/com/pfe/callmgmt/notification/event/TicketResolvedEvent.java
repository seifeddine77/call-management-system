package com.pfe.callmgmt.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketResolvedEvent {
    private UUID ticketId;
    private String numero;
    private String clientNom;
    private String clientEmail;
    private UUID agenceId;
}
