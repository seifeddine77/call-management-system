package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.EvaluateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du moteur d'évaluation de règles (CDC Module 4)
 * Couvre tous les opérateurs de comparaison et logiques.
 */
class RuleEvaluatorTest {

    // --- SonarCloud: extracted string constants ---
    private static final String KEY_OPERATOR = "operator";
    private static final String KEY_CONDITIONS = "conditions";
    private static final String KEY_FIELD = "field";
    private static final String KEY_VALUE = "value";
    private static final String FIELD_TYPE_PIECE = "typePiece";
    private static final String FIELD_AGE_VEHICULE = "ageVehiculeAns";
    private static final String FIELD_KILOMETRAGE = "kilometrage";
    private static final String FIELD_TYPE_SINISTRE = "typeSinistre";
    private static final String VAL_MECANIQUE = "MECANIQUE";
    private static final String VAL_STANDARD = "STANDARD";
    private static final String VAL_PANNE = "PANNE";
    private static final String VAL_ELECTRIQUE = "ELECTRIQUE";
    private static final String VAL_PREMIUM = "PREMIUM";

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RuleEvaluator();
    }

    private EvaluateRequest createRequest(String typePiece, int age, int km,
            String categorie, String sinistre) {
        EvaluateRequest req = new EvaluateRequest();
        req.setTypePiece(typePiece);
        req.setAgeVehiculeAns(age);
        req.setKilometrage(km);
        req.setCategorieClient(categorie);
        req.setTypeSinistre(sinistre);
        return req;
    }

    // ===========================
    // Tests opérateur EQ
    // ===========================
    @Nested
    @DisplayName("Opérateur EQ (Égal)")
    class EqTests {

        @Test
        @DisplayName("EQ string — doit matcher si valeurs égales")
        void eqStringMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_MECANIQUE)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("EQ string — ne doit PAS matcher si valeurs différentes")
        void eqStringNoMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_ELECTRIQUE)));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("EQ string — comparaison insensible à la casse")
        void eqCaseInsensitive() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", "value", "mecanique")));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }
    }

    // ===========================
    // Tests opérateur NEQ
    // ===========================
    @Nested
    @DisplayName("Opérateur NEQ (Différent)")
    class NeqTests {

        @Test
        @DisplayName("NEQ — doit matcher si valeurs différentes")
        void neqMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "NEQ", KEY_VALUE, VAL_ELECTRIQUE)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("NEQ — ne doit PAS matcher si valeurs égales")
        void neqNoMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "NEQ", KEY_VALUE, VAL_MECANIQUE)));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }
    }

    // ===========================
    // Tests opérateurs numériques (LT, GT, LTE, GTE)
    // ===========================
    @Nested
    @DisplayName("Opérateurs numériques")
    class NumericTests {

        @Test
        @DisplayName("LT — age < 5 ans → true pour un véhicule de 3 ans")
        void ltMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("LT — age < 5 ans → false pour un véhicule de 7 ans")
        void ltNoMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5)));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 7, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("GT — km > 150000 → true pour 200000 km")
        void gtMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "GT", "value", 150000)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 200000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("LTE — km <= 100000 → true pour exactement 100000 km")
        void lteExactMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "LTE", "value", 100000)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 100000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("GTE — age >= 10 → true pour 12 ans")
        void gteMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "GTE", "value", 10)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 12, 50000, VAL_STANDARD, VAL_PANNE)));
        }
    }

    // ===========================
    // Tests opérateur CONTAINS
    // ===========================
    @Nested
    @DisplayName("Opérateur CONTAINS")
    class ContainsTests {

        @Test
        @DisplayName("CONTAINS — cherche substring dans la valeur")
        void containsMatch() {
            EvaluateRequest req = createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE);
            req.setPieceReference("ALT-4892-DELCO");

            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of("field", "pieceReference", "op", "CONTAINS", "value", "ALT")));
            assertTrue(evaluator.evaluate(conditions, req));
        }
    }

    // ===========================
    // Tests opérateur IN
    // ===========================
    @Nested
    @DisplayName("Opérateur IN")
    class InTests {

        @Test
        @DisplayName("IN — valeur dans la liste")
        void inMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_SINISTRE, "op", "IN",
                                    "value", List.of("PANNE", "DEFAUT_FABRICATION"))));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_ELECTRIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("IN — valeur PAS dans la liste")
        void inNoMatch() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_SINISTRE, "op", "IN",
                                    "value", List.of("PANNE", "DEFAUT_FABRICATION"))));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_ELECTRIQUE, 3, 50000, "STANDARD", "ACCIDENT")));
        }
    }

    // ===========================
    // Tests opérateurs logiques AND / OR
    // ===========================
    @Nested
    @DisplayName("Opérateurs logiques AND / OR")
    class LogicTests {

        @Test
        @DisplayName("AND — toutes les conditions doivent être vraies")
        void andAllTrue() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_MECANIQUE),
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5),
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "LTE", "value", 100000)));
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 72000, VAL_PREMIUM, VAL_PANNE)));
        }

        @Test
        @DisplayName("AND — une condition fausse → résultat faux")
        void andOneFalse() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_MECANIQUE),
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5),
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "LTE", "value", 100000)));
            // Kilométrage 150000 > 100000 → condition 3 fausse
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 150000, VAL_PREMIUM, VAL_PANNE)));
        }

        @Test
        @DisplayName("OR — au moins une condition vraie suffit")
        void orOneTrue() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "OR",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_ELECTRIQUE),
                            Map.of(KEY_FIELD, FIELD_TYPE_SINISTRE, "op", "EQ", KEY_VALUE, VAL_PANNE)));
            // typePiece=MECANIQUE (faux) mais typeSinistre=PANNE (vrai) → OR → true
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("OR — toutes fausses → résultat faux")
        void orAllFalse() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "OR",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_ELECTRIQUE),
                            Map.of(KEY_FIELD, FIELD_TYPE_SINISTRE, "op", "EQ", "value", "VOL")));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }
    }

    // ===========================
    // Tests cas limites
    // ===========================
    @Nested
    @DisplayName("Cas limites")
    class EdgeCases {

        @Test
        @DisplayName("Conditions vides → false")
        void emptyConditions() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of());
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("Champ inconnu → false (pas de crash)")
        void unknownField() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of("field", "champInexistant", "op", "EQ", "value", "test")));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("Opérateur inconnu → false (pas de crash)")
        void unknownOperator() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "UNKNOWN_OP", KEY_VALUE, VAL_MECANIQUE)));
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 50000, VAL_STANDARD, VAL_PANNE)));
        }

        @Test
        @DisplayName("Scénario CDC réel — Pièce mécanique récente → match")
        void cdcScenarioFavorable() {
            // Règle 1 du CDC: SI typePiece=MECANIQUE ET age<5 ET km<=100000
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_MECANIQUE),
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5),
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "LTE", "value", 100000)));
            // Scénario CDC: alternateur, 3 ans, 72 000 km
            assertTrue(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 3, 72000, VAL_PREMIUM, VAL_PANNE)));
        }

        @Test
        @DisplayName("Scénario CDC réel — Véhicule ancien → no match garantie mécanique")
        void cdcScenarioDefavorable() {
            Map<String, Object> conditions = Map.of(
                    KEY_OPERATOR, "AND",
                    KEY_CONDITIONS, List.of(
                            Map.of(KEY_FIELD, FIELD_TYPE_PIECE, "op", "EQ", KEY_VALUE, VAL_MECANIQUE),
                            Map.of(KEY_FIELD, FIELD_AGE_VEHICULE, "op", "LT", "value", 5),
                            Map.of(KEY_FIELD, FIELD_KILOMETRAGE, "op", "LTE", "value", 100000)));
            // Véhicule 18 ans, 250k km → ne matche PAS cette règle
            assertFalse(evaluator.evaluate(conditions, createRequest(VAL_MECANIQUE, 18, 250000, "STANDARD", "USURE")));
        }
    }
}
