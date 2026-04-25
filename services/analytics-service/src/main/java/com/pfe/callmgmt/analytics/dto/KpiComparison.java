package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la comparaison période-sur-période.
 * Contient les KPIs de la période actuelle, de la période précédente,
 * et les deltas en pourcentage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiComparison {
    private KpiResponse current;
    private KpiResponse previous;

    // Deltas en % (positif = amélioration, négatif = dégradation)
    private double deltaTickets;
    private double deltaResolution;
    private double deltaTempsMoyen;
    private double deltaDecisions;
    private double deltaFavorable;
}
