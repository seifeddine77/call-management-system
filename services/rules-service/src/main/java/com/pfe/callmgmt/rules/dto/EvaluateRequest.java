package com.pfe.callmgmt.rules.dto;

import lombok.Data;

import java.util.Map;

/**
 * DTO de demande d'évaluation — données métier à analyser contre toutes les
 * règles actives.
 * (CDC Module 5.A — Saisie de la Demande)
 *
 * Tous les champs sont optionnels pour supporter à la fois :
 * - L'évaluation complète (decision-service avec toutes les données)
 * - Le test rapide depuis le panel "Tester les règles" (typePiece + age + km)
 *
 * Le champ "data" est un conteneur générique pour les champs définis dynamiquement.
 * Le RuleEvaluator cherche d'abord dans "data", puis fallback sur les champs typés.
 */
@Data
public class EvaluateRequest {

    private String typePiece; // MECANIQUE, ELECTRIQUE, CARROSSERIE, ELECTRONIQUE, PNEUMATIQUE, AUTRE

    private Integer ageVehiculeAns;

    private Integer kilometrage;

    private String categorieClient; // STANDARD, PREMIUM, VIP, PROFESSIONNEL

    private String typeSinistre; // PANNE, ACCIDENT, USURE, DEFAUT_FABRICATION, VOL, AUTRE

    private String pieceReference; // Référence exacte (optionnel — auto-rempli depuis NLP)
    private String transcriptionText; // Texte transcription (optionnel)

    /**
     * Conteneur générique pour les champs dynamiques (CDC 4.B étendu).
     * Les champs définis via FIELD_DEFINITION dans reference_items sont
     * transmis ici. Le RuleEvaluator les résout en priorité avant les
     * champs typés ci-dessus (rétro-compatibilité).
     */
    private Map<String, Object> data;
}

