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
import org.mockito.ArgumentCaptor;
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
                    .createdAt(LocalDateTime.now())
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
}
