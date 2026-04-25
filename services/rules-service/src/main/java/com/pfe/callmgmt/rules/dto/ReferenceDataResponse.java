package com.pfe.callmgmt.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CDC 4.B — Listes de références configurables pour l'éditeur de règles.
 * Retournées par GET /api/rules/references
 * Utilisées par le frontend pour les dropdowns de l'éditeur visuel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceDataResponse {

    /**
     * Types de pièces disponibles dans les règles
     */
    private List<ReferenceItem> typesPiece;

    /**
     * Catégories de clients
     */
    private List<ReferenceItem> categoriesClient;

    /**
     * Types de sinistres
     */
    private List<ReferenceItem> typesSinistre;

    /**
     * Champs disponibles pour les conditions des règles
     */
    private List<FieldDefinition> champsDisponibles;

    /**
     * Opérateurs de comparaison disponibles
     */
    private List<ReferenceItem> operateursComparaison;

    /**
     * Opérateurs logiques (AND/OR)
     */
    private List<ReferenceItem> operateursLogiques;

    /**
     * Actions possibles pour une règle
     */
    private List<ReferenceItem> actions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceItem {
        private String code;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinition {
        private String field; // Ex: "typePiece"
        private String label; // Ex: "Type de pièce"
        private String type; // "string", "number", "enum"
        private List<String> values; // Valeurs possibles si type=enum
    }
}
