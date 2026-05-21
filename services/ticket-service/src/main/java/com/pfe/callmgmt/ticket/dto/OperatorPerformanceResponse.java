package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatorPerformanceResponse {
    private UUID operateurId;
    private String operateurName;
    private int ticketsTraites;
    private int ticketsEnCours;
    private int ticketsResolu;
    private double tauxResolution;
    private double tempsMoyenMinutes;
    private int ticketsCall;
    private int ticketsEmail;
    private int ticketsWeb;
    private int scoreComposite;      // 0-100 score pondéré (résolution 40%, temps 30%, volume 30%)
    private double slaCompliance;    // % tickets résolus en < 4h
}
