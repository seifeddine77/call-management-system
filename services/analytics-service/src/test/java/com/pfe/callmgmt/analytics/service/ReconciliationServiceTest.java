package com.pfe.callmgmt.analytics.service;

import com.pfe.callmgmt.analytics.client.TicketServiceClient;
import com.pfe.callmgmt.analytics.dto.DailyTicketCount;
import com.pfe.callmgmt.analytics.dto.ReconciliationDrift;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.repository.DailyStatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, java.time.Month.JUNE, 10);
    private static final LocalDate FROM = DAY.minusDays(30);
    private static final LocalDate TO = DAY;

    @Mock
    private TicketServiceClient ticketServiceClient;

    @Mock
    private DailyStatRepository dailyStatRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    private DailyTicketCount truth(long total, long call, long email, long web) {
        return DailyTicketCount.builder()
                .date(DAY).totalTickets(total).ticketsCall(call).ticketsEmail(email).ticketsWeb(web)
                .build();
    }

    private DailyStat projected(int total, int call, int email, int web) {
        return DailyStat.builder()
                .date(DAY).totalTickets(total).ticketsCall(call).ticketsEmail(email).ticketsWeb(web)
                .build();
    }

    @Test
    @DisplayName("Aucune dérive quand projection == vérité")
    void noDrift_whenMatching() {
        when(ticketServiceClient.getDailyTicketCounts(FROM, TO)).thenReturn(List.of(truth(5, 3, 1, 1)));
        when(dailyStatRepository.findByDate(DAY)).thenReturn(Optional.of(projected(5, 3, 1, 1)));

        List<ReconciliationDrift> drifts = reconciliationService.reconcile(FROM, TO, false);

        assertThat(drifts).isEmpty();
        verify(dailyStatRepository, never()).save(any());
    }

    @Test
    @DisplayName("Détection seule (apply=false) — retourne les écarts sans écrire")
    void detection_doesNotWrite() {
        // Vérité = 5 créations (4 call, 1 email) ; projection sous-compte (3 call)
        when(ticketServiceClient.getDailyTicketCounts(FROM, TO)).thenReturn(List.of(truth(5, 4, 1, 0)));
        when(dailyStatRepository.findByDate(DAY)).thenReturn(Optional.of(projected(4, 3, 1, 0)));

        List<ReconciliationDrift> drifts = reconciliationService.reconcile(FROM, TO, false);

        assertThat(drifts).extracting(ReconciliationDrift::getField)
                .containsExactlyInAnyOrder("totalTickets", "ticketsCall");
        assertThat(drifts).allMatch(d -> !d.isCorrected());
        assertThat(drifts).filteredOn(d -> d.getField().equals("totalTickets"))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getExpected()).isEqualTo(5);
                    assertThat(d.getActual()).isEqualTo(4);
                });
        verify(dailyStatRepository, never()).save(any());
    }

    @Test
    @DisplayName("Correction (apply=true) — écrase les compteurs de création depuis la vérité")
    void apply_correctsCreationCounters() {
        when(ticketServiceClient.getDailyTicketCounts(FROM, TO)).thenReturn(List.of(truth(5, 4, 1, 0)));
        DailyStat stat = projected(4, 3, 1, 0);
        // Champ hors périmètre — doit rester intact après correction
        stat.setTicketsResolus(2);
        when(dailyStatRepository.findByDate(DAY)).thenReturn(Optional.of(stat));
        when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ReconciliationDrift> drifts = reconciliationService.reconcile(FROM, TO, true);

        ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
        verify(dailyStatRepository).save(captor.capture());
        DailyStat saved = captor.getValue();
        assertThat(saved.getTotalTickets()).isEqualTo(5);
        assertThat(saved.getTicketsCall()).isEqualTo(4);
        assertThat(saved.getTicketsResolus()).isEqualTo(2); // champ de résolution NON touché
        assertThat(drifts).allMatch(ReconciliationDrift::isCorrected);
    }

    @Test
    @DisplayName("Jour sans DailyStat + apply=true — crée la ligne depuis la vérité")
    void apply_createsMissingDailyStat() {
        when(ticketServiceClient.getDailyTicketCounts(FROM, TO)).thenReturn(List.of(truth(3, 1, 1, 1)));
        when(dailyStatRepository.findByDate(DAY)).thenReturn(Optional.empty());
        when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ReconciliationDrift> drifts = reconciliationService.reconcile(FROM, TO, true);

        ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
        verify(dailyStatRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(DAY);
        assertThat(captor.getValue().getTotalTickets()).isEqualTo(3);
        assertThat(drifts).isNotEmpty();
    }

    @Test
    @DisplayName("Jour sans DailyStat + apply=false — écart signalé, aucune écriture")
    void detection_missingDailyStat_reportsWithoutWrite() {
        when(ticketServiceClient.getDailyTicketCounts(FROM, TO)).thenReturn(List.of(truth(2, 2, 0, 0)));
        when(dailyStatRepository.findByDate(DAY)).thenReturn(Optional.empty());

        List<ReconciliationDrift> drifts = reconciliationService.reconcile(FROM, TO, false);

        assertThat(drifts).isNotEmpty();
        assertThat(drifts).filteredOn(d -> d.getField().equals("totalTickets"))
                .singleElement()
                .satisfies(d -> assertThat(d.getActual()).isZero());
        verify(dailyStatRepository, never()).save(any());
    }
}
