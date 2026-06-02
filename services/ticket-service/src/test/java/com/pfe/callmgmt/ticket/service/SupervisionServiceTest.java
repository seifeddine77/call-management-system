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
    }
}
