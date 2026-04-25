package com.pfe.callmgmt.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusChangedEvent {
    private UUID ticketId;
    private String numero;
    private String oldStatut;
    private String newStatut;
    private UUID agenceId;
}
