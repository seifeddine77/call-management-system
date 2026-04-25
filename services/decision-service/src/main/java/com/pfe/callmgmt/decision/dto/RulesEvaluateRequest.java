package com.pfe.callmgmt.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mirrors rules-service EvaluateRequest to send via Feign.
 * Le champ "data" est un conteneur générique pour les champs dynamiques
 * définis via FIELD_DEFINITION dans reference_items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulesEvaluateRequest {

    private String typePiece;
    private Integer ageVehiculeAns;
    private Integer kilometrage;
    private String categorieClient;
    private String typeSinistre;
    private String pieceReference;
    private String transcriptionText;

    /**
     * Conteneur générique pour champs dynamiques (CDC 4.B étendu).
     * Transmis au rules-service pour évaluation des champs non typés.
     */
    private Map<String, Object> data;
}

