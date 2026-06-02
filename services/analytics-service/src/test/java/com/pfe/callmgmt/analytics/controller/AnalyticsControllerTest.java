package com.pfe.callmgmt.analytics.controller;

import com.pfe.callmgmt.analytics.dto.KpiComparison;
import com.pfe.callmgmt.analytics.dto.HourlyData;
import com.pfe.callmgmt.analytics.dto.KpiResponse;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    // --- SonarCloud: extracted string constants ---
    private static final String PERIOD_7D = "7d";

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController controller;

    @Test
    @DisplayName("GET /kpis — retourne KPIs agrégés")
    void getKpis_returnsKpiResponse() {
        KpiResponse kpi = KpiResponse.builder()
                .totalTickets(100).ticketsResolu(60)
                .tauxResolution(60.0).tempsMoyenMinutes(15.0)
                .decisionsAiFavorable(30).decisionsAiDefavorable(10)
                .ticketsCall(40).ticketsEmail(30).ticketsWeb(30)
                .period(PERIOD_7D).build();

        when(analyticsService.getKpis(PERIOD_7D)).thenReturn(kpi);

        ResponseEntity<KpiResponse> response = controller.getKpis(PERIOD_7D);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalTickets()).isEqualTo(100);
        assertThat(response.getBody().getPeriod()).isEqualTo(PERIOD_7D);
    }

    @Test
    @DisplayName("GET /kpis — période 30d")
    void getKpis_30d_passes() {
        KpiResponse kpi = KpiResponse.builder().period("30d").build();
        when(analyticsService.getKpis("30d")).thenReturn(kpi);

        ResponseEntity<KpiResponse> response = controller.getKpis("30d");

        assertThat(response.getBody().getPeriod()).isEqualTo("30d");
    }

    @Test
    @DisplayName("GET /tickets-per-day — retourne données de graphique")
    void getTicketsPerDay_returnsList() {
        List<HourlyData> data = List.of(
                HourlyData.builder().label("2026-03-12").count(50).build(),
                HourlyData.builder().label("2026-03-11").count(40).build()
        );
        when(analyticsService.getTicketsPerDay(PERIOD_7D)).thenReturn(data);

        ResponseEntity<List<HourlyData>> response = controller.getTicketsPerDay(PERIOD_7D);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("GET /daily-stats — retourne stats brutes")
    void getDailyStats_returnsList() {
        DailyStat stat = DailyStat.builder()
                .date(LocalDate.now()).totalTickets(55)
                .ticketsResolus(30).build();
        when(analyticsService.getDailyStats(PERIOD_7D)).thenReturn(List.of(stat));

        ResponseEntity<List<DailyStat>> response = controller.getDailyStats(PERIOD_7D);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getTotalTickets()).isEqualTo(55);
    }

    @Test
    @DisplayName("GET /health — retourne OK")
    void health_returnsOk() {
        ResponseEntity<String> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("OK");
    }

    @Test
    @DisplayName("GET /kpis/comparison — retourne la comparaison période-sur-période")
    void getComparison_returnsKpiComparison() {
        KpiResponse current  = KpiResponse.builder().totalTickets(100).period(PERIOD_7D).build();
        KpiResponse previous = KpiResponse.builder().totalTickets(80).period(PERIOD_7D).build();
        KpiComparison comparison = KpiComparison.builder()
                .current(current).previous(previous)
                .deltaTickets(25.0).deltaResolution(0.0)
                .deltaTempsMoyen(0.0).deltaDecisions(0.0)
                .deltaFavorable(0.0).build();

        when(analyticsService.getComparison(PERIOD_7D)).thenReturn(comparison);

        ResponseEntity<KpiComparison> response = controller.getComparison(PERIOD_7D);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDeltaTickets()).isEqualTo(25.0);
        assertThat(response.getBody().getCurrent().getTotalTickets()).isEqualTo(100);
        verify(analyticsService).getComparison(PERIOD_7D);
    }

    @Test
    @DisplayName("GET /export/csv — retourne CSV avec en-têtes pour données présentes")
    void exportCsv_withData_returnsCsvBytes() {
        LocalDate today = LocalDate.now();
        DailyStat stat = DailyStat.builder()
                .date(today)
                .totalTickets(10)
                .ticketsNouveau(3)
                .ticketsEnCours(4)
                .ticketsResolus(2)
                .ticketsFerme(1)
                .ticketsCall(5)
                .ticketsEmail(3)
                .ticketsWeb(2)
                .decisionsFavorable(4)
                .decisionsDefavorable(2)
                .decisionsEtude(1)
                .tempsMoyenMinutes(java.math.BigDecimal.valueOf(15.5))
                .build();

        KpiResponse kpi = KpiResponse.builder()
                .totalTickets(10).ticketsResolu(2)
                .tauxResolution(20.0).tempsMoyenMinutes(15.5)
                .period("30d").build();

        when(analyticsService.getDailyStats("30d")).thenReturn(List.of(stat));
        when(analyticsService.getKpis("30d")).thenReturn(kpi);

        ResponseEntity<byte[]> response = controller.exportCsv("30d");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("autoassist_analytics_30d.csv");
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("AutoAssist");
        assertThat(csv).contains(today.toString());
        assertThat(csv).contains("15.5");
    }

    @Test
    @DisplayName("GET /export/csv — tempsMoyenMinutes null est remplacé par chaîne vide")
    void exportCsv_nullTempsMoyen_writesEmptyField() {
        LocalDate today = LocalDate.now();
        DailyStat stat = DailyStat.builder()
                .date(today).totalTickets(5)
                .ticketsNouveau(1).ticketsEnCours(1).ticketsResolus(1)
                .ticketsFerme(0).ticketsCall(2).ticketsEmail(1).ticketsWeb(2)
                .decisionsFavorable(1).decisionsDefavorable(0).decisionsEtude(0)
                .tempsMoyenMinutes(null)
                .build();

        KpiResponse kpi = KpiResponse.builder().totalTickets(5).period("30d").build();
        when(analyticsService.getDailyStats("30d")).thenReturn(List.of(stat));
        when(analyticsService.getKpis("30d")).thenReturn(kpi);

        ResponseEntity<byte[]> response = controller.exportCsv("30d");
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        // La dernière valeur de la ligne (tempsMoyenMinutes) doit être vide
        assertThat(csv).contains(today.toString());
        // Vérifie que la ligne se termine par une virgule suivie d'un saut de ligne (valeur vide)
        assertThat(csv).containsPattern(today.toString() + ".*,\n");
    }

    @Test
    @DisplayName("GET /export/csv — liste vide retourne CSV avec uniquement les en-têtes")
    void exportCsv_emptyStats_returnsHeadersOnly() {
        KpiResponse kpi = KpiResponse.builder().totalTickets(0).period("30d").build();
        when(analyticsService.getDailyStats("30d")).thenReturn(List.of());
        when(analyticsService.getKpis("30d")).thenReturn(kpi);

        ResponseEntity<byte[]> response = controller.exportCsv("30d");
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).contains("Date,Total Tickets");
        // Seules les lignes d'en-tête sont présentes
        assertThat(csv.lines().filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("Date")).count()).isZero();
    }
}
