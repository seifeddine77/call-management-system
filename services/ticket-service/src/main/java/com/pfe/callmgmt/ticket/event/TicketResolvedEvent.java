package com.pfe.callmgmt.ticket.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResolvedEvent implements Serializable {
    private UUID ticketId;
    private String numero;
    private String clientNom;
    private String clientEmail;
    private UUID agenceId;
    /** Timestamp de création du ticket — permet de calculer le temps de résolution */
    private LocalDateTime ticketCreatedAt;
}
