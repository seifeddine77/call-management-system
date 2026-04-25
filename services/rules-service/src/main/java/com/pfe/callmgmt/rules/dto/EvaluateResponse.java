package com.pfe.callmgmt.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de résultat d'évaluation — utilisé par le decision-service (CDC Module
 * 5.B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateResponse {

    /**
     * Décision finale du moteur de règles.
     * FAVORABLE, DEFAVORABLE ou ETUDE (si ambiguïté)
     */
    private String decision;

    /**
     * Score de confiance de la décision (0.0 - 100.0)
     * Plus le score est élevé, plus la décision est certaine.
     */
    private Double score;

    /**
     * Motif textuel expliquant la décision
     */
    private String motif;

    /**
     * Noms des règles qui ont matché
     */
    private List<String> rulesApplied;

    /**
     * Nombre total de règles actives évaluées
     */
    private Integer totalRulesEvaluated;

    /**
     * Nombre de règles qui ont matché
     */
    private Integer matchedRules;

    /**
     * Indique si la décision est ambiguë (conflit entre règles)
     * Si true → le decision-service devra consulter l'IA GPT
     */
    private Boolean ambiguous;
}
