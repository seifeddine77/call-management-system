package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Comptage des tickets créés un jour donné, ventilé par canal.
 * Reçu du ticket-service (source de vérité) lors de la réconciliation.
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
