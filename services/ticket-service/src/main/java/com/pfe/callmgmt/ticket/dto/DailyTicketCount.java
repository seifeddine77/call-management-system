package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Comptage des tickets créés un jour donné, ventilé par canal.
 * Source de vérité exposée à l'analytics-service pour la réconciliation des KPIs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTicketCount {
    private LocalDate date;
    private long totalTickets;
    private long ticketsCall;
    private long ticketsEmail;
    private long ticketsWeb;
}
