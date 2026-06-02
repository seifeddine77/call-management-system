package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.OperatorPerformanceResponse;
import com.pfe.callmgmt.ticket.dto.SupervisionAlert;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupervisionServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private SupervisionService supervisionService;

    @Nested
    @DisplayName("getOperatorPerformance")
    class GetOperatorPerformance {

        @Test
        @DisplayName("Retourne les performances avec score composite calculé")
        void returnsPerformancesWithScores() {
            UUID opId = UUID.randomUUID();
            Object[] row = new Object[]{
                    opId, 20, 5, 12, BigDecimal.valueOf(45.5), 10, 5, 5
            };

            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of(new Object[][]{row}));
            when(ticketRepository.countSlaCompliant(any())).thenReturn(10L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(12L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("7d");

            assertThat(result).hasSize(1);
            OperatorPerformanceResponse perf = result.get(0);
            assertThat(perf.getOperateurId()).isEqualTo(opId);
            assertThat(perf.getTicketsTraites()).isEqualTo(20);
            assertThat(perf.getTicketsResolu()).isEqualTo(12);
            assertThat(perf.getTauxResolution()).isGreaterThan(0);
            assertThat(perf.getScoreComposite()).isBetween(0, 100);
        }

        @Test
        @DisplayName("Liste vide si aucun opérateur trouvé")
        void emptyListWhenNoOperators() {
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of());
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("today");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAlerts")
    class GetAlerts {

        @Test
        @DisplayName("Pas d'anomalie → retourne message positif INFO")
        void noAnomalies_returnsPositive() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(SupervisionAlert.Severity.INFO);
        }

        @Test
        @DisplayName("Tickets bloqués > 24h → alerte WARNING")
        void blockedTickets_returnsWarning() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(3L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).isNotEmpty();
            assertThat(alerts.get(0).getType()).isEqualTo(SupervisionAlert.AlertType.TICKET_BLOQUE);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(SupervisionAlert.Severity.WARNING);
            assertThat(alerts.get(0).getCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Tickets bloqués >= 5 → alerte CRITIQUE")
        void manyBlockedTickets_returnsCritique() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(7L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts.get(0).getSeverity()).isEqualTo(SupervisionAlert.Severity.CRITIQUE);
        }

        @Test
        @DisplayName("Tickets non assignés > 2h → alerte NON_ASSIGNE")
        void unassignedTickets_returnsAlert() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(4L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).anyMatch(a -> a.getType() == SupervisionAlert.AlertType.NON_ASSIGNE);
        }

        @Test
        @DisplayName("Tickets non assignés >= 10 → alerte CRITIQUE")
        void manyUnassignedTickets_returnsCritique() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(12L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).anyMatch(a ->
                a.getType() == SupervisionAlert.AlertType.NON_ASSIGNE
                && a.getSeverity() == SupervisionAlert.Severity.CRITIQUE);
        }

        @Test
        @DisplayName("Volume spike — tickets aujourd'hui > 2x la moyenne → alerte VOLUME_SPIKE WARNING")
        void volumeSpike_returnsWarning() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            // today=50, week=70 → avg=10, spike si today > 20
            when(ticketRepository.countCreatedAfter(any()))
                .thenReturn(50L)  // todayCount (1st call: atStartOfDay)
                .thenReturn(70L); // weekCount (2nd call: minusDays(7))
            when(ticketRepository.countSlaCompliant(any())).thenReturn(8L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).anyMatch(a ->
                a.getType() == SupervisionAlert.AlertType.VOLUME_SPIKE
                && a.getSeverity() == SupervisionAlert.Severity.WARNING);
        }

        @Test
        @DisplayName("SLA < 70% et >= 50% → alerte WARNING OPERATEUR_SURCHARGE")
        void slaLow_returnsWarning() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            // SLA = 6/10 = 60% → < 70%, >= 50%
            when(ticketRepository.countSlaCompliant(any())).thenReturn(6L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).anyMatch(a ->
                a.getType() == SupervisionAlert.AlertType.OPERATEUR_SURCHARGE
                && a.getSeverity() == SupervisionAlert.Severity.WARNING);
        }

        @Test
        @DisplayName("SLA < 50% → alerte CRITIQUE OPERATEUR_SURCHARGE")
        void slaVeryLow_returnsCritique() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            // SLA = 4/10 = 40% → < 50%
            when(ticketRepository.countSlaCompliant(any())).thenReturn(4L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(10L);

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            assertThat(alerts).anyMatch(a ->
                a.getType() == SupervisionAlert.AlertType.OPERATEUR_SURCHARGE
                && a.getSeverity() == SupervisionAlert.Severity.CRITIQUE);
        }

        @Test
        @DisplayName("Aucun ticket résolu → SLA globale = 0 (pas d'alerte SLA)")
        void noResolvedTickets_noSlaAlert() {
            when(ticketRepository.countBlockedTickets(any())).thenReturn(0L);
            when(ticketRepository.countOldUnassignedTickets(any())).thenReturn(0L);
            when(ticketRepository.countCreatedAfter(any())).thenReturn(0L);
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L); // totalResolved = 0

            List<SupervisionAlert> alerts = supervisionService.getAlerts();

            // Only the positive INFO alert
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(SupervisionAlert.Severity.INFO);
        }
    }

    @Nested
    @DisplayName("getOperatorPerformance — period variants")
    class PeriodVariants {

        @Test
        @DisplayName("period='today' → filtre depuis début du jour")
        void period_today() {
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of());
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("today");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("period='30d' → filtre depuis 30 jours")
        void period_30d() {
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of());
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("30d");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("period='90d' → filtre depuis 90 jours")
        void period_90d() {
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of());
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("90d");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("period=null → défaut 7 jours")
        void period_null() {
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of());
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("opId comme String (non UUID natif) → parse correctement")
        void operatorId_asString_parsesCorrectly() {
            UUID opId = UUID.randomUUID();
            // row[0] as String instead of UUID
            Object[] row = new Object[]{
                opId.toString(), 10, 2, 7, 30.0, 5, 3, 2
            };
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of(new Object[][]{row}));
            when(ticketRepository.countSlaCompliant(any())).thenReturn(5L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(7L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("week");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOperateurId()).isEqualTo(opId);
        }

        @Test
        @DisplayName("total=0 → tauxResolution=0, scoreComposite calculé sans division par zéro")
        void totalZero_tauxResolutionZero() {
            UUID opId = UUID.randomUUID();
            Object[] row = new Object[]{opId, 0, 0, 0, BigDecimal.ZERO, 0, 0, 0};
            when(ticketRepository.findOperatorPerformanceRaw(any())).thenReturn(List.of(new Object[][]{row}));
            when(ticketRepository.countSlaCompliant(any())).thenReturn(0L);
            when(ticketRepository.countResolvedAfter(any())).thenReturn(0L);

            List<OperatorPerformanceResponse> result = supervisionService.getOperatorPerformance("7d");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTauxResolution()).isEqualTo(0.0);
        }
    }
}
