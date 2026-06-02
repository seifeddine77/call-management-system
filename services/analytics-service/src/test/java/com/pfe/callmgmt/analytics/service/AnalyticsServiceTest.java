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

    // --- SonarCloud: extracted string constants ---
    private static final String STATUT_NOUVEAU = "NOUVEAU";
    private static final String STATUT_EN_COURS = "EN_COURS";
    private static final String STATUT_FERME = "FERME";

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

            analyticsService.onTicketStatusChanged(STATUT_NOUVEAU, STATUT_EN_COURS);

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

            analyticsService.onTicketStatusChanged(STATUT_EN_COURS, STATUT_FERME);

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

            analyticsService.onTicketStatusChanged(STATUT_NOUVEAU, STATUT_EN_COURS);

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

        @Test
        @DisplayName("Incrémente ETUDE")
        void incrementDecision_etude() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementDecision("ETUDE");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            assertThat(captor.getValue().getDecisionsEtude()).isEqualTo(4);
        }

        @Test
        @DisplayName("Décision nulle ignorée silencieusement")
        void incrementDecision_null_ignored() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementDecision(null);

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            // compteurs inchangés
            assertThat(captor.getValue().getDecisionsFavorable()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("incrementTicketCreated - canaux supplémentaires")
    class IncrementTicketCreatedExtra {

        @Test
        @DisplayName("Canal EMAIL incrémente ticketsEmail")
        void incrementTicketCreated_email() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketCreated("EMAIL");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            assertThat(captor.getValue().getTicketsEmail()).isEqualTo(16);
        }

        @Test
        @DisplayName("Canal WEB incrémente ticketsWeb")
        void incrementTicketCreated_web() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketCreated("WEB");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            assertThat(captor.getValue().getTicketsWeb()).isEqualTo(16);
        }

        @Test
        @DisplayName("Canal inconnu ignoré silencieusement")
        void incrementTicketCreated_unknownCanal_ignored() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.incrementTicketCreated("AUTRE");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            // total incrémenté mais aucun canal spécifique
            assertThat(captor.getValue().getTotalTickets()).isEqualTo(51);
            assertThat(captor.getValue().getTicketsCall()).isEqualTo(20);
            assertThat(captor.getValue().getTicketsEmail()).isEqualTo(15);
            assertThat(captor.getValue().getTicketsWeb()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("onTicketStatusChanged - cas supplémentaires")
    class OnTicketStatusChangedExtra {

        @Test
        @DisplayName("Transition avec ancien statut null ne lance pas d'exception")
        void statusChanged_nullOldStatut_noException() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(
                () -> analyticsService.onTicketStatusChanged(null, STATUT_EN_COURS)
            );
        }

        @Test
        @DisplayName("Transition vers statut inconnu ignorée silencieusement")
        void statusChanged_unknownNewStatut_ignored() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(
                () -> analyticsService.onTicketStatusChanged(STATUT_NOUVEAU, "INCONNU")
            );
        }

        @Test
        @DisplayName("Transition RESOLU ne modifie pas le compteur (append-only)")
        void statusChanged_resolvedStatut_noCounterChange() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.of(todayStat));
            when(dailyStatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.onTicketStatusChanged("EN_COURS", "RESOLU");

            ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
            verify(dailyStatRepository).save(captor.capture());
            // EN_COURS décrémenté, RESOLU non incrémenté (append-only via incrementTicketResolved)
            assertThat(captor.getValue().getTicketsEnCours()).isEqualTo(11);
            assertThat(captor.getValue().getTicketsResolus()).isEqualTo(30); // inchangé
        }
    }

    @Nested
    @DisplayName("getComparison")
    class GetComparison {

        @Test
        @DisplayName("Comparaison 7d retourne les deltas corrects")
        void getComparison_7d_returnsDeltas() {
            DailyStat current = DailyStat.builder()
                    .date(LocalDate.now())
                    .totalTickets(100).ticketsResolus(80)
                    .decisionsFavorable(40).decisionsDefavorable(20).decisionsEtude(10)
                    .tempsMoyenMinutes(BigDecimal.valueOf(10.0))
                    .totalResolutionMinutes(BigDecimal.valueOf(800.0))
                    .resolvedCountToday(80)
                    .build();

            DailyStat previous = DailyStat.builder()
                    .date(LocalDate.now().minusDays(8))
                    .totalTickets(80).ticketsResolus(60)
                    .decisionsFavorable(30).decisionsDefavorable(15).decisionsEtude(8)
                    .tempsMoyenMinutes(BigDecimal.valueOf(12.0))
                    .totalResolutionMinutes(BigDecimal.valueOf(720.0))
                    .resolvedCountToday(60)
                    .build();

            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(current))
                    .thenReturn(List.of(previous));

            var comparison = analyticsService.getComparison("7d");

            assertThat(comparison.getCurrent().getTotalTickets()).isEqualTo(100);
            assertThat(comparison.getPrevious().getTotalTickets()).isEqualTo(80);
            assertThat(comparison.getDeltaTickets()).isGreaterThan(0); // 100 vs 80 = +25%
        }
    }

    @Nested
    @DisplayName("getDailyStats - périodes")
    class GetDailyStats {

        @Test
        @DisplayName("Période 'today' retourne les stats du jour")
        void getDailyStats_today() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat));

            List<DailyStat> result = analyticsService.getDailyStats("today");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Période 'month' retourne les stats du mois")
        void getDailyStats_month() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat));

            List<DailyStat> result = analyticsService.getDailyStats("month");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Période 'week' est synonyme de 7d")
        void getDailyStats_week() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of());

            List<DailyStat> result = analyticsService.getDailyStats("week");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Période '90d' est synonyme de year")
        void getDailyStats_90d() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat));

            List<DailyStat> result = analyticsService.getDailyStats("90d");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Période 'year' utilise 90 jours")
        void getDailyStats_year() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of(todayStat));

            List<DailyStat> result = analyticsService.getDailyStats("year");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Période inconnue utilise 7 jours par défaut")
        void getDailyStats_unknownPeriod_defaults7d() {
            when(dailyStatRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                    .thenReturn(List.of());

            List<DailyStat> result = analyticsService.getDailyStats("invalid");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("nightlyAggregation")
    class NightlyAggregation {

        @Test
        @DisplayName("nightlyAggregation crée ou récupère la stat du jour")
        void nightlyAggregation_createsTodayStat() {
            when(dailyStatRepository.findByDate(LocalDate.now())).thenReturn(Optional.empty());
            when(dailyStatRepository.save(any())).thenReturn(todayStat);

            analyticsService.nightlyAggregation();

            verify(dailyStatRepository).save(any());
        }
    }
}
