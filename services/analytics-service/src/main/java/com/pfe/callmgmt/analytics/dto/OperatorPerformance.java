package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatorPerformance {
    private UUID operateurId;
    private String nom;
    private int ticketsTraites;
    private double tempsMoyenMinutes;
    private double tauxResolution;
}
