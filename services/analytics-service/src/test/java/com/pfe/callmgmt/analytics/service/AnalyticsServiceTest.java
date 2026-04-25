package com.pfe.callmgmt.analytics.service;

import com.pfe.callmgmt.analytics.dto.HourlyData;
import com.pfe.callmgmt.analytics.dto.KpiResponse;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.repository.DailyStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private DailyStatRepository dailyStatRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private DailyStat todayStat;

    @BeforeEach
    void setUp() {
        todayStat = DailyStat.builder()
                .date(LocalDate.now())
                .totalTickets(50)
                .ticketsResolus(30)
                .ticketsNouveau(8)
                .ticketsEnCours(12)
                .ticketsFerme(20)
                .tempsMoyenMinutes(BigDecimal.valueOf(12.5))
                .totalResolutionMinutes(BigDecimal.valueOf(375.0))
                .resolvedCountToday(30)
                .decisionsFavorable(15)
                .decisionsDefavorable(8)
                .decisionsEtude(3)
                .ticketsCall(20)
                .ticketsEmail(15)
                .ticketsWeb(15)
                .build();
    }

    @Nested
    @DisplayName("getKpis")
    class GetKpis {

        @Test
        @DisplayName("KPIs pour 7d avec données — utilise les vrais compteurs par statut")
        void getKpis_7d_returnsAggregatedKpis() {
            DailyStat yesterday = DailyStat.builder()
                    .date(LocalDate.now().minusDays(1))
                    .totalTickets(40)
                    .ticketsResolus(25)
                    .ticketsNouveau(5)
                    .ticketsEnCours(10)
                    .ticketsFerme(15)
                    .tempsMoyenMinutes(BigDecimal.valueOf(10.0))
                    .totalResolutionMinutes(BigDecimal.valueOf(250.0))
                    .resolvedCountToday(25)
                    .decisionsFavorable(10)
                    .decisionsDefavorable(5)
                    .decisionsEtude(2)
                    .ticketsCall(15)
                    .ticketsEmail(10)
                    .ticketsWeb(15)
                    .build();

            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat, yesterday));

            KpiResponse kpis = analyticsService.getKpis("7d");

            assertThat(kpis.getTotalTickets()).isEqualTo(90);
            assertThat(kpis.getTicketsResolu()).isEqualTo(55);
            assertThat(kpis.getTicketsNouveau()).isEqualTo(13); // 8 + 5
            assertThat(kpis.getTicketsEnCours()).isEqualTo(22); // 12 + 10
            assertThat(kpis.getTicketsFerme()).isEqualTo(35); // 20 + 15
            assertThat(kpis.getDecisionsAiFavorable()).isEqualTo(25);
            assertThat(kpis.getDecisionsAiDefavorable()).isEqualTo(13);
            assertThat(kpis.getTicketsCall()).isEqualTo(35);
            assertThat(kpis.getPeriod()).isEqualTo("7d");
            assertThat(kpis.getTauxResolution()).isGreaterThan(0);
            // Temps moyen = (375+250)/(30+25) = 625/55 ≈ 11.36
            assertThat(kpis.getTempsMoyenMinutes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("KPIs sans données retourne zéros")
        void getKpis_noData_returnsZeros() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of());

            KpiResponse kpis = analyticsService.getKpis("today");

            assertThat(kpis.getTotalTickets()).isZero();
            assertThat(kpis.getTauxResolution()).isZero();
            assertThat(kpis.getTempsMoyenMinutes()).isZero();
            assertThat(kpis.getTicketsNouveau()).isZero();
            assertThat(kpis.getTicketsEnCours()).isZero();
            assertThat(kpis.getPeriod()).isEqualTo("today");
        }
    }

    @Nested
    @DisplayName("getTicketsPerDay")
    class GetTicketsPerDay {

        @Test
        @DisplayName("Retourne les données formatées pour graphique")
        void getTicketsPerDay_returnsFormattedData() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat));

            List<HourlyData> data = analyticsService.getTicketsPerDay("7d");

            assertThat(data).hasSize(1);
            assertThat(data.get(0).getCount()).isEqualTo(50);
            assertThat(data.get(0).getLabel()).contains(String.valueOf(LocalDate.now().getYear()));
        }
    }

    @Nested
    @DisplayName("incrementTicketCreated")
    class IncrementTicketCreated {

        @Test
        @DisplayName("Incrémente le compteur total, canal CALL, et ticketsNouveau")
        void incrementTicketCreated_call_incrementsBoth() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketCreated("CALL");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTotalTickets()).isEqualTo(51);
            assertThat(saved.getTicketsCall()).isEqualTo(21);
            assertThat(saved.getTicketsNouveau()).isEqualTo(9); // 8 + 1
        }

        @Test
        @DisplayName("Crée une entrée si elle n'existe pas")
        void incrementTicketCreated_noEntry_createsNew() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.empty());
            DailyStat newStat = DailyStat.builder().date(LocalDate.now()).build();
            when(dailyStatRepository.save(any())).thenReturn(newStat).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketCreated("WEB");

            verify(dailyStatRepository, atLeast(1)).save(any());
        }
    }

    @Nested
    @DisplayName("incrementTicketResolved")
    class IncrementTicketResolved {

        @Test
        @DisplayName("Calcule et met à jour le temps moyen de résolution")
        void incrementTicketResolved_withCreatedAt_computesDuration() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Ticket created 45 minutes ago
            LocalDateTime createdAt = LocalDateTime.now().minusMinutes(45);
            analyticsService.incrementTicketResolved(createdAt);

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTicketsResolus()).isEqualTo(31);
            assertThat(saved.getResolvedCountToday()).isEqualTo(31); // 30 + 1
            assertThat(saved.getTotalResolutionMinutes()).isGreaterThan(BigDecimal.valueOf(375));
            assertThat(saved.getTempsMoyenMinutes()).isNotNull();
            assertThat(saved.getTempsMoyenMinutes().doubleValue()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Fonctionne même sans ticketCreatedAt (rétro-compatibilité)")
        void incrementTicketResolved_nullCreatedAt_stillIncrements() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketResolved(null);

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTicketsResolus()).isEqualTo(31);
            // Temps moyen inchangé car pas de createdAt
            assertThat(saved.getTempsMoyenMinutes()).isEqualTo(BigDecimal.valueOf(12.5));
        }
    }

    @Nested
    @DisplayName("onTicketStatusChanged")
    class OnTicketStatusChanged {

        @Test
        @DisplayName("Transition NOUVEAU → EN_COURS décrémente nouveau et incrémente enCours")
        void statusChanged_nouveauToEnCours() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.onTicketStatusChanged("NOUVEAU", "EN_COURS");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTicketsNouveau()).isEqualTo(7);  // 8 - 1
            assertThat(saved.getTicketsEnCours()).isEqualTo(13); // 12 + 1
        }

        @Test
        @DisplayName("Transition EN_COURS → FERME décrémente enCours et incrémente fermé")
        void statusChanged_enCoursToFerme() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.onTicketStatusChanged("EN_COURS", "FERME");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTicketsEnCours()).isEqualTo(11); // 12 - 1
            assertThat(saved.getTicketsFerme()).isEqualTo(21);   // 20 + 1
        }

        @Test
        @DisplayName("Protection contre les compteurs négatifs")
        void statusChanged_doesNotGoNegative() {
            todayStat.setTicketsNouveau(0);
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.onTicketStatusChanged("NOUVEAU", "EN_COURS");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());

            DailyStat saved = captor.getValue();
            assertThat(saved.getTicketsNouveau()).isZero(); // Ne passe pas en négatif
            assertThat(saved.getTicketsEnCours()).isEqualTo(13);
        }
    }

    @Nested
    @DisplayName("incrementDecision")
    class IncrementDecision {

        @Test
        @DisplayName("Incrémente FAVORABLE")
        void incrementDecision_favorable() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementDecision("FAVORABLE");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            assertThat(captor.getValue().getDecisionsFavorable()).isEqualTo(16);
        }

        @Test
        @DisplayName("Incrémente DEFAVORABLE")
        void incrementDecision_defavorable() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementDecision("DEFAVORABLE");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            assertThat(captor.getValue().getDecisionsDefavorable()).isEqualTo(9);
        }
    }
}
