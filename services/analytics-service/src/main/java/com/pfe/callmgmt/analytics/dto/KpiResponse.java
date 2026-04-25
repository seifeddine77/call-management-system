package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiResponse {
    private int totalTickets;
    private int ticketsNouveau;
    private int ticketsEnCours;
    private int ticketsResolu;
    private int ticketsFerme;
    private double tauxResolution;
    private double tempsMoyenMinutes;
    private int decisionsAiFavorable;
    private int decisionsAiDefavorable;
    private int decisionsAiEtude;
    private int ticketsCall;
    private int ticketsEmail;
    private int ticketsWeb;
    private String period;
}
