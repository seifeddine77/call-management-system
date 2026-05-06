package com.pfe.callmgmt.analytics.controller;

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
}
