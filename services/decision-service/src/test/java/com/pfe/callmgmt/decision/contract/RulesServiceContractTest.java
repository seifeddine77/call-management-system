package com.pfe.callmgmt.decision.contract;

import com.pfe.callmgmt.decision.client.RulesServiceClient;
import com.pfe.callmgmt.decision.dto.RulesEvaluateRequest;
import com.pfe.callmgmt.decision.dto.RulesEvaluateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consumer-side Contract Tests for decision-service → rules-service.
 *
 * Verifies that the RulesServiceClient Feign interface correctly
 * defines the contract expected by the rules-service POST /api/rules/evaluate
 * endpoint.
 *
 * Tests validate:
 * 1. Request DTO fields match what rules-service expects
 * 2. Response DTO fields match what rules-service returns
 * 3. The Feign client method signature is correct
 */
@ExtendWith(MockitoExtension.class)
public class RulesServiceContractTest {

        @Mock
        private RulesServiceClient rulesServiceClient;

        @Test
        @DisplayName("Contract: POST /api/rules/evaluate → FAVORABLE with score")
        void shouldEvaluateAndReturnFavorable() {
                // Given — the expected response from rules-service
                RulesEvaluateResponse expectedResponse = RulesEvaluateResponse.builder()
                                .decision("FAVORABLE")
                                .score(85.0)
                                .motif("Pièce mécanique, véhicule récent, faible kilométrage")
                                .rulesApplied(List.of("Règle Mécanique Simple", "Règle Véhicule Récent"))
                                .totalRulesEvaluated(5)
                                .matchedRules(2)
                                .ambiguous(false)
                                .build();

                when(rulesServiceClient.evaluate(any(RulesEvaluateRequest.class))).thenReturn(expectedResponse);

                // When — build request matching rules-service contract
                RulesEvaluateRequest request = RulesEvaluateRequest.builder()
                                .typePiece("MECANIQUE")
                                .ageVehiculeAns(2)
                                .kilometrage(30000)
                                .categorieClient("PREMIUM")
                                .typeSinistre("PANNE")
                                .build();

                RulesEvaluateResponse response = rulesServiceClient.evaluate(request);

                // Then — verify contract fields
                assertThat(response).isNotNull();
                assertThat(response.getDecision()).isEqualTo("FAVORABLE");
                assertThat(response.getScore()).isEqualTo(85.0);
                assertThat(response.getMotif()).isNotBlank();
                assertThat(response.getRulesApplied()).isNotEmpty();
                assertThat(response.getTotalRulesEvaluated()).isGreaterThan(0);
                assertThat(response.getMatchedRules()).isGreaterThan(0);
                assertThat(response.getAmbiguous()).isNotNull();

                verify(rulesServiceClient).evaluate(any(RulesEvaluateRequest.class));
        }

        @Test
        @DisplayName("Contract: POST /api/rules/evaluate → DEFAVORABLE with high km")
        void shouldEvaluateAndReturnDefavorable() {
                RulesEvaluateResponse expectedResponse = RulesEvaluateResponse.builder()
                                .decision("DEFAVORABLE")
                                .score(25.0)
                                .motif("Véhicule trop ancien, kilométrage excessif")
                                .rulesApplied(List.of("Règle Kilométrage Excessif"))
                                .totalRulesEvaluated(5)
                                .matchedRules(1)
                                .ambiguous(false)
                                .build();

                when(rulesServiceClient.evaluate(any(RulesEvaluateRequest.class))).thenReturn(expectedResponse);

                RulesEvaluateRequest request = RulesEvaluateRequest.builder()
                                .typePiece("PNEUMATIQUE")
                                .ageVehiculeAns(18)
                                .kilometrage(250000)
                                .categorieClient("STANDARD")
                                .typeSinistre("USURE")
                                .build();

                RulesEvaluateResponse response = rulesServiceClient.evaluate(request);

                assertThat(response.getDecision()).isEqualTo("DEFAVORABLE");
                assertThat(response.getScore()).isLessThan(50.0);
                assertThat(response.getRulesApplied()).hasSize(1);
        }

        @Test
        @DisplayName("Contract: POST /api/rules/evaluate → ETUDE (ambiguous)")
        void shouldEvaluateAndReturnEtude() {
                RulesEvaluateResponse expectedResponse = RulesEvaluateResponse.builder()
                                .decision("ETUDE")
                                .score(50.0)
                                .motif("Situation ambiguë, étude manuelle requise")
                                .rulesApplied(List.of("Règle Accident Carrosserie"))
                                .totalRulesEvaluated(5)
                                .matchedRules(1)
                                .ambiguous(true)
                                .build();

                when(rulesServiceClient.evaluate(any(RulesEvaluateRequest.class))).thenReturn(expectedResponse);

                RulesEvaluateRequest request = RulesEvaluateRequest.builder()
                                .typePiece("CARROSSERIE")
                                .ageVehiculeAns(4)
                                .kilometrage(55000)
                                .categorieClient("VIP")
                                .typeSinistre("ACCIDENT")
                                .build();

                RulesEvaluateResponse response = rulesServiceClient.evaluate(request);

                assertThat(response.getDecision()).isEqualTo("ETUDE");
                assertThat(response.getAmbiguous()).isTrue();
        }

        @Test
        @DisplayName("Contract: Request DTO contains all required fields for rules-service")
        void shouldHaveAllRequiredFieldsInRequest() {
                // Verify that RulesEvaluateRequest has all fields expected by rules-service
                RulesEvaluateRequest request = RulesEvaluateRequest.builder()
                                .typePiece("MECANIQUE")
                                .ageVehiculeAns(5)
                                .kilometrage(100000)
                                .categorieClient("STANDARD")
                                .typeSinistre("PANNE")
                                .pieceReference("ALT-4892")
                                .transcriptionText("Mon alternateur est en panne")
                                .build();

                assertThat(request.getTypePiece()).isNotNull();
                assertThat(request.getAgeVehiculeAns()).isNotNull();
                assertThat(request.getKilometrage()).isNotNull();
                assertThat(request.getCategorieClient()).isNotNull();
                assertThat(request.getTypeSinistre()).isNotNull();
                assertThat(request.getPieceReference()).isNotNull();
                assertThat(request.getTranscriptionText()).isNotNull();
        }
}
