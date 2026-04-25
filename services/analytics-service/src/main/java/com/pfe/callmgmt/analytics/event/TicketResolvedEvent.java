package com.pfe.callmgmt.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    /** Timestamp de création du ticket — permet de calculer le temps de résolution */
    private LocalDateTime ticketCreatedAt;
}
