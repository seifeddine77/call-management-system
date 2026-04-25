package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.EvaluateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Moteur d'évaluation des conditions JSONB (CDC Module 4.A/4.C)
 *
 * Évalue si un ensemble de conditions JSONB est satisfait par les données
 * métier d'une demande.
 * Supporte :
 * - Opérateurs logiques : AND, OR
 * - Opérateurs de comparaison : EQ, NEQ, LT, GT, LTE, GTE, CONTAINS, IN
 * - Types de champs : String, Integer, Double
 */
@Component
@Slf4j
public class RuleEvaluator {

    /**
     * Évalue les conditions JSONB contre les données d'une demande.
     *
     * @param conditionsMap le JSONB {"operator":"AND","conditions":[...]}
     * @param request       les données métier à évaluer
     * @return true si toutes les conditions sont satisfaites
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(Map<String, Object> conditionsMap, EvaluateRequest request) {
        String operator = (String) conditionsMap.getOrDefault("operator", "AND");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) conditionsMap.get("conditions");

        if (conditions == null || conditions.isEmpty()) {
            log.warn("Règle sans conditions — considérée comme non-matchante");
            return false;
        }

        if ("OR".equalsIgnoreCase(operator)) {
            // Au moins une condition doit être vraie
            return conditions.stream().anyMatch(c -> evaluateCondition(c, request));
        } else {
            // AND : toutes les conditions doivent être vraies
            return conditions.stream().allMatch(c -> evaluateCondition(c, request));
        }
    }

    /**
     * Évalue une condition individuelle.
     * Supporte deux formats JSONB :
     *   - Ancien: {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"}
     *   - Nouveau: {"field": "typePiece", "operator": "=", "value": "MECANIQUE"}
     */
    private boolean evaluateCondition(Map<String, Object> condition, EvaluateRequest request) {
        String field = (String) condition.get("field");
        // Support both "operator" (frontend format) and "op" (legacy format)
        String op = (String) condition.getOrDefault("operator", condition.get("op"));
        Object expectedValue = condition.get("value");

        if (field == null || op == null || expectedValue == null) {
            log.warn("Condition invalide : field={}, op={}, value={}", field, op, expectedValue);
            return false;
        }

        // Normalize operator: convert symbolic operators (=, !=, <, >) to backend codes (EQ, NEQ, LT, GT)
        op = normalizeOperator(op);

        // Récupérer la valeur réelle depuis la demande
        Object actualValue = getFieldValue(field, request);
        if (actualValue == null) {
            log.debug("Champ '{}' non renseigné dans la demande", field);
            return false;
        }

        try {
            return compareValues(op, actualValue, expectedValue);
        } catch (Exception e) {
            log.warn("Erreur évaluation condition: field={}, op={}, expected={}, actual={} — {}",
                    field, op, expectedValue, actualValue, e.getMessage());
            return false;
        }
    }

    /**
     * Normalise les opérateurs symboliques vers les codes backend.
     * Supporte les deux formats pour compatibilité front et back.
     */
    private String normalizeOperator(String op) {
        return switch (op.trim()) {
            case "=" -> "EQ";
            case "!=" -> "NEQ";
            case "<" -> "LT";
            case ">" -> "GT";
            case "<=" -> "LTE";
            case ">=" -> "GTE";
            default -> op.toUpperCase(); // EQ, NEQ, LT, GT, etc. — already normalized
        };
    }

    /**
     * Récupère la valeur d'un champ depuis la demande.
     * Priorité : 1) Map dynamique "data"  2) Getters typés (rétro-compat)
     */
    private Object getFieldValue(String field, EvaluateRequest request) {
        // 1. Chercher dans la Map dynamique (champs définis en BDD)
        if (request.getData() != null && request.getData().containsKey(field)) {
            return request.getData().get(field);
        }

        // 2. Fallback rétro-compatible sur les champs typés
        return switch (field.toLowerCase()) {
            case "typepiece" -> request.getTypePiece();
            case "agevehiculeans" -> request.getAgeVehiculeAns();
            case "kilometrage" -> request.getKilometrage();
            case "categorieclient" -> request.getCategorieClient();
            case "typesinistre" -> request.getTypeSinistre();
            case "piecereference" -> request.getPieceReference();
            case "transcriptiontext", "transcription" -> request.getTranscriptionText();
            default -> {
                log.debug("Champ '{}' non trouvé dans les getters typés ni dans data", field);
                yield null;
            }
        };
    }

    /**
     * Compare deux valeurs selon l'opérateur.
     * Supporte : EQ, NEQ, LT, GT, LTE, GTE, CONTAINS, IN
     */
    @SuppressWarnings("unchecked")
    private boolean compareValues(String op, Object actual, Object expected) {
        return switch (op.toUpperCase()) {
            case "EQ" -> equalsIgnoreType(actual, expected);
            case "NEQ" -> !equalsIgnoreType(actual, expected);
            case "LT" -> toDouble(actual) < toDouble(expected);
            case "GT" -> toDouble(actual) > toDouble(expected);
            case "LTE" -> toDouble(actual) <= toDouble(expected);
            case "GTE" -> toDouble(actual) >= toDouble(expected);
            case "CONTAINS" -> actual.toString().toLowerCase().contains(expected.toString().toLowerCase());
            case "IN" -> {
                // expected est une liste, actual doit être dans cette liste
                if (expected instanceof List) {
                    yield ((List<Object>) expected).stream()
                            .anyMatch(item -> equalsIgnoreType(actual, item));
                }
                yield false;
            }
            default -> {
                log.warn("Opérateur inconnu: '{}'", op);
                yield false;
            }
        };
    }

    /**
     * Comparaison indifférente au type (String/Number).
     */
    private boolean equalsIgnoreType(Object a, Object b) {
        if (a == null || b == null)
            return a == b;
        // Comparer comme strings en ignorant la casse
        return a.toString().equalsIgnoreCase(b.toString());
    }

    /**
     * Convertit une valeur en double pour les comparaisons numériques.
     */
    private double toDouble(Object value) {
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
}
