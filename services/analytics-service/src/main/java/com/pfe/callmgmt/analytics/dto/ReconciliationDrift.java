package com.pfe.callmgmt.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Écart détecté entre la projection analytics (DailyStat) et la source de vérité
 * (ticket-service) pour un champ de comptage donné, un jour donné.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDrift {
    private LocalDate date;
    private String field;
    /** Valeur attendue (source de vérité ticket-service). */
    private long expected;
    /** Valeur projetée actuelle (DailyStat). */
    private long actual;
    /** true si l'écart a été corrigé pendant cette passe. */
    private boolean corrected;
}
