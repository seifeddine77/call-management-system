package com.pfe.callmgmt.analytics.dto;

import lombok.Data;

/**
 * Répartition par statut COURANT des tickets créés sur une plage, renvoyée par le
 * ticket-service (/api/tickets/stats/by-period). Sert à fournir une ventilation de
 * statut cohérente avec le total de la période dans les KPIs (au lieu de sommer les
 * instantanés journaliers de daily_stats).
 */
@Data
public class TicketStatsDto {
    private long nouveau;
    private long enCours;
    private long resolu;
    private long ferme;
    private long total;
}
