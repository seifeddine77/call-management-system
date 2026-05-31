package com.pfe.callmgmt.rules.controller;

import com.pfe.callmgmt.rules.dto.*;
import com.pfe.callmgmt.rules.entity.RuleVersion;
import com.pfe.callmgmt.rules.service.ReferenceDataService;
import com.pfe.callmgmt.rules.service.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires — RuleController (Mockito pur)
 *
 * Vérifient la logique du contrôleur sans charger Spring :
 * - CRUD complet (POST, GET, PUT, DELETE)
 * - Versioning automatique (CDC 4.D)
 * - Toggle activation/désactivation
 * - Évaluation des règles (CDC 4.E)
 * - Listes de références (CDC 4.B)
 * - Codes HTTP corrects
 */
@ExtendWith(MockitoExtension.class)
class RuleControllerIntegrationTest {

        @Mock
        private RuleService ruleService;
        @Mock
        private ReferenceDataService referenceDataService;

        @InjectMocks
        private RuleController ruleController;

        private static final UUID RULE_ID = UUID.randomUUID();

        // --- SonarCloud: extracted string constants ---
        private static final String ACTION_FAVORABLE = "FAVORABLE";
        private static final String ACTION_DEFAVORABLE = "DEFAVORABLE";
        private static final String RULE_NOM = "Test Intégration — Pièce électronique";
        private static final String CREATED_BY = "TestSupervisor";
        private static final String TEST_ADMIN = "TestAdmin";
        private static final String KEY_OPERATOR = "operator";
        private static final String KEY_CONDITIONS = "conditions";

        private RuleResponse sampleRule;

        @BeforeEach
        void setUp() {
                sampleRule = RuleResponse.builder()
                                .id(RULE_ID)
                                .nom(RULE_NOM)
                                .description("Règle de test")
                                .conditions(Map.of(KEY_OPERATOR, "AND",
                                                KEY_CONDITIONS, List.of(
                                                                Map.of("field", "typePiece", "op", "EQ", "value",
                                                                                "ELECTRONIQUE"),
                                                                Map.of("field", "ageVehiculeAns", "op", "LTE", "value",
                                                                                3))))
                                .action(ACTION_FAVORABLE)
                                .actionMotif("Pièce électronique sous garantie constructeur")
                                .priorite((short) 7)
                                .actif(true)
                                .version((short) 1)
                                .createdByName(CREATED_BY)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
        }

        // =====================================
        // 1. TEST CRUD — CREATE
        // =====================================

    @Test
    @DisplayName("POST /api/rules — Créer une règle → 201 CREATED")
    void createRule() {
        when(ruleService.createRule(any(CreateRuleRequest.class), any(), any())).thenReturn(sampleRule);

        CreateRuleRequest request = new CreateRuleRequest();
        request.setNom(RULE_NOM);
        request.setConditions(Map.of(KEY_OPERATOR, "AND", KEY_CONDITIONS, List.of()));
        request.setAction(ACTION_FAVORABLE);
        request.setActionMotif("Pièce électronique sous garantie constructeur");
        request.setPriorite((short) 7);

        ResponseEntity<RuleResponse> response = ruleController.create(
                request, UUID.randomUUID().toString(), CREATED_BY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(RULE_ID);
        assertThat(response.getBody().getNom()).isEqualTo(RULE_NOM);
        assertThat(response.getBody().getAction()).isEqualTo(ACTION_FAVORABLE);
        assertThat(response.getBody().getPriorite()).isEqualTo((short) 7);
        assertThat(response.getBody().getActif()).isTrue();
        assertThat(response.getBody().getVersion()).isEqualTo((short) 1);
        assertThat(response.getBody().getCreatedByName()).isEqualTo(CREATED_BY);

        verify(ruleService).createRule(any(), any(), eq(CREATED_BY));
    }

        // =====================================
        // 2. TEST CRUD — READ
        // =====================================

        @Test
        @DisplayName("GET /api/rules — Liste paginée avec règles")
        void listRules() {
                Page<RuleResponse> page = new PageImpl<>(List.of(sampleRule));
                when(ruleService.findAll(any(), any(Pageable.class))).thenReturn(page);

                Pageable pageable = PageRequest.of(0, 20);
                ResponseEntity<Page<RuleResponse>> response = ruleController.list(null, pageable);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getTotalElements()).isEqualTo(1);
                assertThat(response.getBody().getContent().get(0).getNom()).isNotNull();
                assertThat(response.getBody().getContent().get(0).getAction()).isNotNull();
        }

        @Test
        @DisplayName("GET /api/rules?actif=true — Filtrer les règles actives uniquement")
        void listActiveRules() {
                Page<RuleResponse> page = new PageImpl<>(List.of(sampleRule));
                when(ruleService.findAll(eq(true), any(Pageable.class))).thenReturn(page);

                Pageable pageable = PageRequest.of(0, 20);
                ResponseEntity<Page<RuleResponse>> response = ruleController.list(true, pageable);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getContent()).allMatch(r -> r.getActif());
        }

    @Test
    @DisplayName("GET /api/rules/{id} — Détail d'une règle existante")
    void getRuleById() {
        when(ruleService.findById(RULE_ID)).thenReturn(sampleRule);

        ResponseEntity<RuleResponse> response = ruleController.getById(RULE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(RULE_ID);
        assertThat(response.getBody().getNom()).isEqualTo(RULE_NOM);
    }

        @Test
        @DisplayName("GET /api/rules/{id} — RuntimeException si ID inexistant → propagée")
        void getRuleNotFound() {
                UUID randomId = UUID.randomUUID();
                when(ruleService.findById(randomId)).thenThrow(
                                new RuntimeException("Règle non trouvée: " + randomId));

                try {
                        ruleController.getById(randomId);
                } catch (RuntimeException e) {
                        assertThat(e.getMessage()).contains("Règle non trouvée");
                }
        }

        // =====================================
        // 3. TEST CRUD — UPDATE + VERSIONING (CDC 4.D)
        // =====================================

        @Test
        @DisplayName("PUT /api/rules/{id} — Modifier une règle → version 2")
        void updateRuleWithVersioning() {
                RuleResponse updated = RuleResponse.builder()
                                .id(RULE_ID)
                                .nom("Test Intégration — Pièce électronique (modifiée)")
                                .conditions(Map.of(KEY_OPERATOR, "AND",
                                                KEY_CONDITIONS, List.of(
                                                                Map.of("field", "typePiece", "op", "EQ", "value",
                                                                                "ELECTRONIQUE"),
                                                                Map.of("field", "ageVehiculeAns", "op", "LTE", "value",
                                                                                5),
                                                                Map.of("field", "kilometrage", "op", "LT", "value",
                                                                                80000))))
                                .action(ACTION_FAVORABLE)
                                .actionMotif("Garantie étendue")
                                .priorite((short) 8)
                                .actif(true)
                                .version((short) 2)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                when(ruleService.updateRule(eq(RULE_ID), any(UpdateRuleRequest.class), any(), any()))
                                .thenReturn(updated);

                UpdateRuleRequest request = new UpdateRuleRequest();
                request.setNom("Test Intégration — Pièce électronique (modifiée)");
                request.setConditions(Map.of(KEY_OPERATOR, "AND", KEY_CONDITIONS, List.of()));
                request.setAction(ACTION_FAVORABLE);
                request.setActionMotif("Garantie étendue");
                request.setPriorite((short) 8);

                ResponseEntity<RuleResponse> response = ruleController.update(
                                RULE_ID, request, UUID.randomUUID().toString(), TEST_ADMIN);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getVersion()).isEqualTo((short) 2);
                assertThat(response.getBody().getNom()).contains("modifiée");
                assertThat(response.getBody().getPriorite()).isEqualTo((short) 8);
        }

        @Test
        @DisplayName("GET /api/rules/{id}/history — Historique contient version 1")
        void checkVersionHistory() {
                RuleVersion version = new RuleVersion();
                version.setVersion((short) 1);
                version.setChangedByName(TEST_ADMIN);
                version.setConditions(Map.of(KEY_OPERATOR, "AND"));
                version.setChangedAt(LocalDateTime.now());

                when(ruleService.getHistory(RULE_ID)).thenReturn(List.of(version));

                ResponseEntity<List<RuleVersion>> response = ruleController.history(RULE_ID);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).hasSize(1);
                assertThat(response.getBody().get(0).getVersion()).isEqualTo((short) 1);
                assertThat(response.getBody().get(0).getChangedByName()).isEqualTo(TEST_ADMIN);
        }

        // =====================================
        // 4. TEST TOGGLE (CDC 4.A)
        // =====================================

        @Test
        @DisplayName("PATCH /api/rules/{id}/toggle — Désactiver → actif=false")
        void toggleRuleOff() {
                RuleResponse toggled = RuleResponse.builder()
                                .id(RULE_ID).nom("Test").conditions(Map.of())
                                .action(ACTION_FAVORABLE).actionMotif("Test").actif(false).build();
                when(ruleService.toggleRule(RULE_ID)).thenReturn(toggled);

                ResponseEntity<RuleResponse> response = ruleController.toggle(RULE_ID);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getActif()).isFalse();
        }

        @Test
        @DisplayName("PATCH /api/rules/{id}/toggle — Réactiver → actif=true")
        void toggleRuleOn() {
                RuleResponse toggled = RuleResponse.builder()
                                .id(RULE_ID).nom("Test").conditions(Map.of())
                                .action(ACTION_FAVORABLE).actionMotif("Test").actif(true).build();
                when(ruleService.toggleRule(RULE_ID)).thenReturn(toggled);

                ResponseEntity<RuleResponse> response = ruleController.toggle(RULE_ID);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getActif()).isTrue();
        }

        // =====================================
        // 5. TEST ÉVALUATION (CDC 4.E)
        // =====================================

        @Test
        @DisplayName("POST /api/rules/evaluate — Scénario FAVORABLE")
        void evaluateFavorable() {
                EvaluateResponse evalResp = EvaluateResponse.builder()
                                .decision(ACTION_FAVORABLE).score(80.0).ambiguous(false)
                                .motif("Pièce mécanique sous garantie")
                                .rulesApplied(List.of("Règle Mécanique Simple"))
                                .totalRulesEvaluated(5).matchedRules(2).build();
                when(ruleService.evaluate(any(EvaluateRequest.class))).thenReturn(evalResp);

                EvaluateRequest request = new EvaluateRequest();
                request.setTypePiece("MECANIQUE");
                request.setAgeVehiculeAns(3);
                request.setKilometrage(72000);
                request.setCategorieClient("PREMIUM");
                request.setTypeSinistre("PANNE");

                ResponseEntity<EvaluateResponse> response = ruleController.evaluate(request);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getDecision()).isEqualTo(ACTION_FAVORABLE);
                assertThat(response.getBody().getScore()).isGreaterThan(60.0);
                assertThat(response.getBody().getAmbiguous()).isFalse();
                assertThat(response.getBody().getRulesApplied()).isNotEmpty();
                assertThat(response.getBody().getMotif()).isNotEmpty();
        }

        @Test
        @DisplayName("POST /api/rules/evaluate — Scénario DEFAVORABLE")
        void evaluateDefavorable() {
                EvaluateResponse evalResp = EvaluateResponse.builder()
                                .decision(ACTION_DEFAVORABLE).score(75.0).ambiguous(false)
                                .motif("Kilométrage excessif")
                                .rulesApplied(List.of("Règle Kilométrage Excessif"))
                                .totalRulesEvaluated(5).matchedRules(1).build();
                when(ruleService.evaluate(any(EvaluateRequest.class))).thenReturn(evalResp);

                EvaluateRequest request = new EvaluateRequest();
                request.setTypePiece("PNEUMATIQUE");
                request.setAgeVehiculeAns(18);
                request.setKilometrage(250000);
                request.setCategorieClient("STANDARD");
                request.setTypeSinistre("USURE");

                ResponseEntity<EvaluateResponse> response = ruleController.evaluate(request);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getDecision()).isEqualTo(ACTION_DEFAVORABLE);
                assertThat(response.getBody().getScore()).isGreaterThan(60.0);
                assertThat(response.getBody().getAmbiguous()).isFalse();
        }

        @Test
        @DisplayName("POST /api/rules/evaluate — Scénario ETUDE (carrosserie accident)")
        void evaluateEtude() {
                EvaluateResponse evalResp = EvaluateResponse.builder()
                                .decision("ETUDE").score(50.0).ambiguous(true)
                                .motif("Situation ambiguë")
                                .rulesApplied(List.of("Règle Accident Carrosserie"))
                                .totalRulesEvaluated(5).matchedRules(1).build();
                when(ruleService.evaluate(any(EvaluateRequest.class))).thenReturn(evalResp);

                EvaluateRequest request = new EvaluateRequest();
                request.setTypePiece("CARROSSERIE");
                request.setAgeVehiculeAns(4);
                request.setKilometrage(55000);
                request.setCategorieClient("VIP");
                request.setTypeSinistre("ACCIDENT");

                ResponseEntity<EvaluateResponse> response = ruleController.evaluate(request);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getDecision()).isEqualTo("ETUDE");
                assertThat(response.getBody().getAmbiguous()).isTrue();
        }

        // =====================================
        // 6. TEST RÉFÉRENCES (CDC 4.B)
        // =====================================

        @Test
        @DisplayName("GET /api/rules/references — Listes de référence complètes")
        void getReferences() {
                ReferenceDataResponse refData = ReferenceDataResponse.builder()
                                .typesPiece(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("MECANIQUE")
                                                                .label("Mécanique").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("CARROSSERIE")
                                                                .label("Carrosserie").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("ELECTRONIQUE")
                                                                .label("Électronique").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("PNEUMATIQUE")
                                                                .label("Pneumatique").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("VITRAGE")
                                                                .label("Vitrage").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("AUTRE")
                                                                .label("Autre").build()))
                                .categoriesClient(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("STANDARD")
                                                                .label("Standard").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("PREMIUM")
                                                                .label("Premium").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("VIP").label("VIP")
                                                                .build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("ENTREPRISE")
                                                                .label("Entreprise").build()))
                                .typesSinistre(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("PANNE")
                                                                .label("Panne").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("ACCIDENT")
                                                                .label("Accident").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("VOL").label("Vol")
                                                                .build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("USURE")
                                                                .label("Usure").build(),
                                                ReferenceDataResponse.ReferenceItem.builder()
                                                                .code("CATASTROPHE_NATURELLE").label("Catastrophe")
                                                                .build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("AUTRE")
                                                                .label("Autre").build()))
                                .champsDisponibles(List.of(
                                                ReferenceDataResponse.FieldDefinition.builder().field("typePiece")
                                                                .label("Type de pièce").type("enum").build(),
                                                ReferenceDataResponse.FieldDefinition.builder().field("ageVehiculeAns")
                                                                .label("Âge véhicule").type("number").build(),
                                                ReferenceDataResponse.FieldDefinition.builder().field("kilometrage")
                                                                .label("Kilométrage").type("number").build(),
                                                ReferenceDataResponse.FieldDefinition.builder().field("categorieClient")
                                                                .label("Catégorie client").type("enum").build(),
                                                ReferenceDataResponse.FieldDefinition.builder().field("typeSinistre")
                                                                .label("Type sinistre").type("enum").build(),
                                                ReferenceDataResponse.FieldDefinition.builder().field("montantEstime")
                                                                .label("Montant estimé").type("number").build()))
                                .operateursComparaison(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("EQ").label("Égal")
                                                                .build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("NEQ")
                                                                .label("Différent").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("GT")
                                                                .label("Supérieur").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("GTE")
                                                                .label("Supérieur ou égal").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("LT")
                                                                .label("Inférieur").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("LTE")
                                                                .label("Inférieur ou égal").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("IN")
                                                                .label("Dans la liste").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("NOT_IN")
                                                                .label("Hors liste").build()))
                                .operateursLogiques(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("AND").label("ET")
                                                                .build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("OR").label("OU")
                                                                .build()))
                                .actions(List.of(
                                                ReferenceDataResponse.ReferenceItem.builder().code("FAVORABLE")
                                                                .label("Favorable").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("DEFAVORABLE")
                                                                .label("Défavorable").build(),
                                                ReferenceDataResponse.ReferenceItem.builder().code("ETUDE")
                                                                .label("Étude").build()))
                                .build();
                when(referenceDataService.getReferenceData()).thenReturn(refData);

                ResponseEntity<ReferenceDataResponse> response = ruleController.references();

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getTypesPiece()).hasSize(6);
                assertThat(response.getBody().getCategoriesClient()).hasSize(4);
                assertThat(response.getBody().getTypesSinistre()).hasSize(6);
                assertThat(response.getBody().getChampsDisponibles()).hasSize(6);
                assertThat(response.getBody().getOperateursComparaison()).hasSize(8);
                assertThat(response.getBody().getOperateursLogiques()).hasSize(2);
                assertThat(response.getBody().getActions()).hasSize(3);
        }

        // =====================================
        // 7. TEST CRUD — DELETE
        // =====================================

        @Test
        @DisplayName("DELETE /api/rules/{id} — Supprimer la règle de test → 204")
        void deleteRule() {
                doNothing().when(ruleService).deleteRule(RULE_ID);

                ResponseEntity<Void> response = ruleController.delete(RULE_ID);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(ruleService).deleteRule(RULE_ID);
        }

        // =====================================
        // 8. TEST CREATE — sans userId (anonyme)
        // =====================================

    @Test
    @DisplayName("POST /api/rules — Créer sans userId → ANONYMOUS par défaut")
    void createRuleAnonymous() {
        when(ruleService.createRule(any(CreateRuleRequest.class), eq(null), eq("ANONYMOUS"))).thenReturn(sampleRule);

        CreateRuleRequest request = new CreateRuleRequest();
        request.setNom("Test Rule");
        request.setConditions(Map.of(KEY_OPERATOR, "AND", KEY_CONDITIONS, List.of()));
        request.setAction(ACTION_FAVORABLE);
        request.setActionMotif("Test");

        ResponseEntity<RuleResponse> response = ruleController.create(request, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(ruleService).createRule(any(), eq(null), eq("ANONYMOUS"));
    }
}
