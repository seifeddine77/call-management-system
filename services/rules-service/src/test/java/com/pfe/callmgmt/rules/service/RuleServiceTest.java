package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.*;
import com.pfe.callmgmt.rules.entity.Rule;
import com.pfe.callmgmt.rules.entity.RuleVersion;
import com.pfe.callmgmt.rules.repository.RuleRepository;
import com.pfe.callmgmt.rules.repository.RuleVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleVersionRepository ruleVersionRepository;

    @Mock
    private RuleEvaluator ruleEvaluator;

    @InjectMocks
    private RuleService ruleService;

    private Map<String, Object> validConditions() {
        Map<String, Object> cond = new HashMap<>();
        cond.put("field", "ageVehiculeAns");
        cond.put("operator", "GT");
        cond.put("value", 10);
        return Map.of("operator", "AND", "conditions", List.of(cond));
    }

    @Nested
    @DisplayName("createRule")
    class CreateRule {

        @Test
        @DisplayName("Crée une règle avec versioning initial à 1")
        void createsRuleWithVersion1() {
            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("Age véhicule > 10 ans")
                    .description("Véhicule trop ancien")
                    .conditions(validConditions())
                    .action("DEFAVORABLE")
                    .actionMotif("Véhicule hors garantie")
                    .priorite((short) 8)
                    .scoreImpact((short) -10)
                    .build();

            UUID userId = UUID.randomUUID();
            Rule savedRule = Rule.builder()
                    .id(UUID.randomUUID())
                    .nom(request.getNom())
                    .description(request.getDescription())
                    .conditions(request.getConditions())
                    .action("DEFAVORABLE")
                    .actionMotif(request.getActionMotif())
                    .priorite((short) 8)
                    .scoreImpact((short) -10)
                    .actif(true)
                    .version((short) 1)
                    .createdBy(userId)
                    .createdByName("Admin")
                    .createdAt(LocalDateTime.of(2026, java.time.Month.JUNE, 5, 12, 0, 0))
                    .build();

            when(ruleRepository.save(any(Rule.class))).thenReturn(savedRule);

            RuleResponse response = ruleService.createRule(request, userId, "Admin");

            assertThat(response.getNom()).isEqualTo("Age véhicule > 10 ans");
            assertThat(response.getAction()).isEqualTo("DEFAVORABLE");
            assertThat(response.getVersion()).isEqualTo((short) 1);
            assertThat(response.getActif()).isTrue();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Retourne la règle trouvée")
        void returnsRuleWhenFound() {
            UUID ruleId = UUID.randomUUID();
            Rule rule = Rule.builder()
                    .id(ruleId)
                    .nom("Test Rule")
                    .action("FAVORABLE")
                    .actif(true)
                    .version((short) 1)
                    .build();

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));

            RuleResponse response = ruleService.findById(ruleId);

            assertThat(response.getId()).isEqualTo(ruleId);
            assertThat(response.getNom()).isEqualTo("Test Rule");
        }

        @Test
        @DisplayName("Lève EntityNotFoundException si non trouvée")
        void throwsWhenNotFound() {
            UUID ruleId = UUID.randomUUID();
            when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.findById(ruleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("toggleRule")
    class ToggleRule {

        @Test
        @DisplayName("Toggle actif/inactif inverse le booléen")
        void togglesActiveState() {
            UUID ruleId = UUID.randomUUID();
            Rule rule = Rule.builder()
                    .id(ruleId)
                    .nom("Test Rule")
                    .action("FAVORABLE")
                    .actif(true)
                    .version((short) 1)
                    .build();

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RuleResponse response = ruleService.toggleRule(ruleId);

            assertThat(response.getActif()).isFalse();
        }
    }

    @Nested
    @DisplayName("evaluate")
    class Evaluate {

        @Test
        @DisplayName("Aucune règle active → ETUDE ambiguë")
        void noActiveRules_returnsEtude() {
            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of());

            EvaluateRequest request = EvaluateRequest.builder().build();
            EvaluateResponse response = ruleService.evaluate(request);

            assertThat(response.getDecision()).isEqualTo("ETUDE");
            assertThat(response.getAmbiguous()).isTrue();
            assertThat(response.getTotalRulesEvaluated()).isZero();
        }

        @Test
        @DisplayName("Une règle FAVORABLE matche → décision FAVORABLE")
        void oneRuleMatches_returnsFavorable() {
            Rule rule = Rule.builder()
                    .id(UUID.randomUUID())
                    .nom("Client VIP")
                    .action("FAVORABLE")
                    .actionMotif("Client fidèle")
                    .priorite((short) 10)
                    .scoreImpact((short) 15)
                    .actif(true)
                    .build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of(rule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateRequest request = EvaluateRequest.builder().build();
            EvaluateResponse response = ruleService.evaluate(request);

            assertThat(response.getDecision()).isEqualTo("FAVORABLE");
            assertThat(response.getMatchedRules()).isEqualTo(1);
            assertThat(response.getRulesApplied()).contains("Client VIP");
        }

        @Test
        @DisplayName("Aucune règle ne matche → ETUDE ambiguë")
        void noMatch_returnsEtude() {
            Rule rule = Rule.builder()
                    .id(UUID.randomUUID())
                    .nom("Unused Rule")
                    .action("FAVORABLE")
                    .actif(true)
                    .build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of(rule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(false);

            EvaluateRequest request = EvaluateRequest.builder().build();
            EvaluateResponse response = ruleService.evaluate(request);

            assertThat(response.getDecision()).isEqualTo("ETUDE");
            assertThat(response.getAmbiguous()).isTrue();
            assertThat(response.getMatchedRules()).isZero();
        }
    }

    @Nested
    @DisplayName("deleteRule")
    class DeleteRule {

        @Test
        @DisplayName("Supprime la règle existante")
        void deletesExistingRule() {
            UUID ruleId = UUID.randomUUID();
            Rule rule = Rule.builder().id(ruleId).nom("To Delete").build();

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));

            ruleService.deleteRule(ruleId);

            verify(ruleRepository).delete(rule);
        }

        @Test
        @DisplayName("Lève EntityNotFoundException pour une règle inexistante")
        void throwsForNonexistentRule() {
            UUID ruleId = UUID.randomUUID();
            when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.deleteRule(ruleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateRule")
    class UpdateRule {

        private UpdateRuleRequest buildUpdateRequest(String nom, String action, String motif, Short priorite, Short score) {
            UpdateRuleRequest r = new UpdateRuleRequest();
            r.setNom(nom);
            r.setDescription("desc");
            r.setConditions(validConditions());
            r.setAction(action);
            r.setActionMotif(motif);
            r.setPriorite(priorite);
            r.setScoreImpact(score);
            return r;
        }

        @Test
        @DisplayName("Met à jour la règle et archive la version précédente")
        void updatesRuleAndArchivesPreviousVersion() {
            UUID ruleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Rule existingRule = Rule.builder()
                    .id(ruleId).nom("Old Name").description("Old desc")
                    .conditions(validConditions()).action("DEFAVORABLE")
                    .actionMotif("Old motif").priorite((short) 5).scoreImpact((short) 0)
                    .actif(true).version((short) 1).build();

            UpdateRuleRequest request = buildUpdateRequest("New Name", "FAVORABLE", "New motif", (short) 8, (short) 10);

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(existingRule));
            when(ruleVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RuleResponse response = ruleService.updateRule(ruleId, request, userId, "Admin");

            verify(ruleVersionRepository).save(any(RuleVersion.class));
            assertThat(response.getNom()).isEqualTo("New Name");
            assertThat(response.getAction()).isEqualTo("FAVORABLE");
            assertThat(response.getVersion()).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("priorite/scoreImpact null dans la requête conserve les valeurs existantes")
        void updateRule_nullPrioriteAndScore_keepsExisting() {
            UUID ruleId = UUID.randomUUID();

            Rule existingRule = Rule.builder()
                    .id(ruleId).nom("Rule").conditions(validConditions()).action("FAVORABLE")
                    .priorite((short) 7).scoreImpact((short) 5).actif(true).version((short) 1).build();

            // null priorite/scoreImpact → doit conserver les valeurs existantes
            UpdateRuleRequest request = buildUpdateRequest("Rule", "FAVORABLE", "m", null, null);

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(existingRule));
            when(ruleVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RuleResponse response = ruleService.updateRule(ruleId, request, UUID.randomUUID(), "User");

            assertThat(response.getPriorite()).isEqualTo((short) 7);
            assertThat(response.getScoreImpact()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("Lève EntityNotFoundException si règle non trouvée")
        void updateRule_notFound_throws() {
            UUID ruleId = UUID.randomUUID();
            when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

            UpdateRuleRequest request = buildUpdateRequest("x", "FAVORABLE", "m", null, null);

            assertThatThrownBy(() -> ruleService.updateRule(ruleId, request, UUID.randomUUID(), "User"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("Sans filtre retourne toutes les règles")
        void findAll_noFilter_returnsAll() {
            Rule rule = Rule.builder().id(UUID.randomUUID()).nom("R1").action("FAVORABLE")
                    .actif(true).version((short) 1).build();
            Page<Rule> page = new PageImpl<>(List.of(rule));
            when(ruleRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

            Page<RuleResponse> result = ruleService.findAll(null, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getNom()).isEqualTo("R1");
        }

        @Test
        @DisplayName("Avec filtre actif=true retourne uniquement les règles actives")
        void findAll_withActifFilter_returnsFiltered() {
            Rule rule = Rule.builder().id(UUID.randomUUID()).nom("Active").action("FAVORABLE")
                    .actif(true).version((short) 1).build();
            Page<Rule> page = new PageImpl<>(List.of(rule));
            when(ruleRepository.findByActif(eq(true), any())).thenReturn(page);

            Page<RuleResponse> result = ruleService.findAll(true, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getActif()).isTrue();
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("Retourne l'historique des versions")
        void getHistory_returnsVersionList() {
            UUID ruleId = UUID.randomUUID();
            Rule rule = Rule.builder().id(ruleId).nom("R").action("FAVORABLE")
                    .actif(true).version((short) 2).build();
            RuleVersion v1 = RuleVersion.builder().ruleId(ruleId).version((short) 1).build();

            when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
            when(ruleVersionRepository.findByRuleIdOrderByVersionDesc(ruleId)).thenReturn(List.of(v1));

            List<RuleVersion> history = ruleService.getHistory(ruleId);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getVersion()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("Lève EntityNotFoundException si règle non trouvée")
        void getHistory_notFound_throws() {
            UUID ruleId = UUID.randomUUID();
            when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.getHistory(ruleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("evaluate - cas étendus")
    class EvaluateExtended {

        @Test
        @DisplayName("Une règle DEFAVORABLE matche → décision DEFAVORABLE")
        void oneRuleMatches_returnsDefavorable() {
            Rule rule = Rule.builder()
                    .id(UUID.randomUUID()).nom("Véhicule ancien")
                    .action("DEFAVORABLE").actionMotif("Trop vieux")
                    .priorite((short) 8).scoreImpact((short) -15)
                    .actif(true).build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of(rule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateResponse response = ruleService.evaluate(EvaluateRequest.builder().build());

            assertThat(response.getDecision()).isEqualTo("DEFAVORABLE");
            assertThat(response.getAmbiguous()).isFalse();
        }

        @Test
        @DisplayName("Règle ETUDE matchée → décision ETUDE (priorité sécurité)")
        void etudeRule_takesOverFavorable() {
            Rule etudeRule = Rule.builder()
                    .id(UUID.randomUUID()).nom("Cas complexe")
                    .action("ETUDE").actionMotif("Vérification manuelle requise")
                    .priorite((short) 9).actif(true).build();
            Rule favorableRule = Rule.builder()
                    .id(UUID.randomUUID()).nom("Client VIP")
                    .action("FAVORABLE").actionMotif("Client fidèle")
                    .priorite((short) 5).actif(true).build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc())
                    .thenReturn(List.of(etudeRule, favorableRule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateResponse response = ruleService.evaluate(EvaluateRequest.builder().build());

            assertThat(response.getDecision()).isEqualTo("ETUDE");
            assertThat(response.getAmbiguous()).isTrue();
        }

        @Test
        @DisplayName("Conflit FAVORABLE vs DEFAVORABLE → règle plus prioritaire gagne")
        void conflictFavorableDefavorable_highestPriorityWins() {
            Rule favorable = Rule.builder()
                    .id(UUID.randomUUID()).nom("Favorable High")
                    .action("FAVORABLE").actionMotif("F")
                    .priorite((short) 10).scoreImpact((short) 10).actif(true).build();
            Rule defavorable = Rule.builder()
                    .id(UUID.randomUUID()).nom("Defavorable Low")
                    .action("DEFAVORABLE").actionMotif("D")
                    .priorite((short) 3).scoreImpact((short) -5).actif(true).build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc())
                    .thenReturn(List.of(favorable, defavorable));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateResponse response = ruleService.evaluate(EvaluateRequest.builder().build());

            assertThat(response.getDecision()).isEqualTo("FAVORABLE");
            assertThat(response.getAmbiguous()).isTrue();
            assertThat(response.getMatchedRules()).isEqualTo(2);
        }

        @Test
        @DisplayName("Transcription avec mots-clés garantie booste le score FAVORABLE")
        void evaluate_withPositiveTranscription_boostsScore() {
            Rule rule = Rule.builder()
                    .id(UUID.randomUUID()).nom("Client VIP")
                    .action("FAVORABLE").actionMotif("OK")
                    .priorite((short) 5).scoreImpact((short) 0).actif(true).build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of(rule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateRequest request = EvaluateRequest.builder()
                    .transcriptionText("La voiture est sous garantie constructeur et entretien régulier effectué")
                    .build();

            EvaluateResponse response = ruleService.evaluate(request);

            assertThat(response.getDecision()).isEqualTo("FAVORABLE");
            assertThat(response.getScore()).isGreaterThan(65.0); // bonus transcription
        }

        @Test
        @DisplayName("Transcription avec mots-clés fraude réduit le score DEFAVORABLE")
        void evaluate_withFraudTranscription_lowersScore() {
            Rule rule = Rule.builder()
                    .id(UUID.randomUUID()).nom("Fraude détectée")
                    .action("DEFAVORABLE").actionMotif("Fraude")
                    .priorite((short) 9).scoreImpact((short) 0).actif(true).build();

            when(ruleRepository.findByActifTrueOrderByPrioriteDesc()).thenReturn(List.of(rule));
            when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

            EvaluateRequest request = EvaluateRequest.builder()
                    .transcriptionText("fausse déclaration et kilométrage falsifié")
                    .build();

            EvaluateResponse response = ruleService.evaluate(request);

            assertThat(response.getDecision()).isEqualTo("DEFAVORABLE");
            assertThat(response.getScore()).isLessThanOrEqualTo(35.0); // malus fraude
        }
    }

    @Nested
    @DisplayName("validateConditionsStructure")
    class ValidateConditions {

        @Test
        @DisplayName("conditions null → IllegalArgumentException")
        void nullConditions_throws() {
            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(null).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nulles");
        }

        @Test
        @DisplayName("conditions sans 'conditions' list → IllegalArgumentException")
        void missingConditionsList_throws() {
            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R")
                    .conditions(Map.of("operator", "AND"))
                    .action("FAVORABLE")
                    .build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatoire");
        }

        @Test
        @DisplayName("conditions list vide → IllegalArgumentException")
        void emptyConditionsList_throws() {
            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R")
                    .conditions(Map.of("operator", "AND", "conditions", List.of()))
                    .action("FAVORABLE")
                    .build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vide");
        }

        @Test
        @DisplayName("Opérateur IN avec valeur non-list → IllegalArgumentException")
        void inOperatorWithNonList_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "categorie");
            cond.put("operator", "IN");
            cond.put("value", "VIP"); // devrait être une liste
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("liste");
        }

        @Test
        @DisplayName("Opérateur global invalide (MAYBE) → IllegalArgumentException")
        void invalidGlobalOperator_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "age"); cond.put("operator", "GT"); cond.put("value", 5);
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("operator", "MAYBE"); // invalide
            conditions.put("conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AND");
        }

        @Test
        @DisplayName("'conditions' n'est pas une List → IllegalArgumentException")
        void conditionsNotAList_throws() {
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("operator", "AND");
            conditions.put("conditions", "not-a-list"); // String au lieu de List

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("liste");
        }

        @Test
        @DisplayName("Condition n'est pas un Map → IllegalArgumentException")
        void conditionNotAMap_throws() {
            Map<String, Object> conditions = Map.of(
                "operator", "AND",
                "conditions", List.of("invalid-string") // pas un Map
            );
            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("objet");
        }

        @Test
        @DisplayName("Condition avec 'field' vide → IllegalArgumentException")
        void blankField_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "   "); // blank
            cond.put("operator", "GT"); cond.put("value", 5);
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("field");
        }

        @Test
        @DisplayName("Condition sans 'operator' ni 'op' → IllegalArgumentException")
        void missingOperator_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "age"); cond.put("value", 5); // pas d'operator
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("opérateur");
        }

        @Test
        @DisplayName("Condition avec opérateur inconnu (BETWEEN) → IllegalArgumentException")
        void unknownOperator_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "age"); cond.put("operator", "BETWEEN"); cond.put("value", 5);
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Opérateur inconnu");
        }

        @Test
        @DisplayName("Condition sans clé 'value' → IllegalArgumentException")
        void missingValueKey_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "age"); cond.put("operator", "GT"); // pas de value
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("Condition avec value=null → IllegalArgumentException")
        void nullValue_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "age"); cond.put("operator", "GT"); cond.put("value", null);
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("Opérateur NOT_IN avec valeur non-list → IllegalArgumentException")
        void notInOperatorWithNonList_throws() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "categorie"); cond.put("operator", "NOT_IN"); cond.put("value", "VIP");
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("R").conditions(conditions).action("FAVORABLE").build();

            assertThatThrownBy(() -> ruleService.createRule(request, UUID.randomUUID(), "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("liste");
        }

        @Test
        @DisplayName("Condition valide avec opérateur 'op' (alias) → création réussie")
        void validConditionWithOpAlias_succeeds() {
            Map<String, Object> cond = new HashMap<>();
            cond.put("field", "ageVehiculeAns");
            cond.put("op", "LTE"); // alias pour "operator"
            cond.put("value", 5);
            Map<String, Object> conditions = Map.of("operator", "AND", "conditions", List.of(cond));

            CreateRuleRequest request = CreateRuleRequest.builder()
                    .nom("Test op alias").conditions(conditions)
                    .action("FAVORABLE").actionMotif("OK")
                    .priorite((short) 5).scoreImpact((short) 0).build();

            Rule saved = Rule.builder().id(UUID.randomUUID()).nom("Test op alias")
                    .action("FAVORABLE").actif(true).version((short) 1).build();
            when(ruleRepository.save(any())).thenReturn(saved);

            RuleResponse response = ruleService.createRule(request, UUID.randomUUID(), "user");
            assertThat(response.getNom()).isEqualTo("Test op alias");
        }
    }
}
