package com.pfe.callmgmt.analytics.controller;

import com.pfe.callmgmt.analytics.dto.HourlyData;
import com.pfe.callmgmt.analytics.dto.KpiComparison;
import com.pfe.callmgmt.analytics.dto.KpiResponse;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "KPIs, statistiques et rapports pour le dashboard superviseur")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/kpis")
    @Operation(summary = "KPIs agrégés", description = "Retourne les KPIs pour la période spécifiée (today, 7d, 30d, 90d)")
    public ResponseEntity<KpiResponse> getKpis(
            @RequestParam(defaultValue = "7d") String period) {
        return ResponseEntity.ok(analyticsService.getKpis(period));
    }

    @GetMapping("/kpis/comparison")
    @Operation(summary = "Comparaison période-sur-période", description = "Retourne les KPIs actuels vs la période précédente avec les deltas en %")
    public ResponseEntity<KpiComparison> getComparison(
            @RequestParam(defaultValue = "7d") String period) {
        return ResponseEntity.ok(analyticsService.getComparison(period));
    }

    @GetMapping("/tickets-per-day")
    @Operation(summary = "Tickets par jour", description = "Données pour les graphiques de tendance")
    public ResponseEntity<List<HourlyData>> getTicketsPerDay(
            @RequestParam(defaultValue = "7d") String period) {
        return ResponseEntity.ok(analyticsService.getTicketsPerDay(period));
    }

    @GetMapping("/daily-stats")
    @Operation(summary = "Statistiques quotidiennes brutes")
    public ResponseEntity<List<DailyStat>> getDailyStats(
            @RequestParam(defaultValue = "7d") String period) {
        return ResponseEntity.ok(analyticsService.getDailyStats(period));
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export CSV", description = "Exporte les statistiques quotidiennes en CSV")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "30d") String period) {
        List<DailyStat> stats = analyticsService.getDailyStats(period);
        KpiResponse kpis = analyticsService.getKpis(period);

        StringBuilder csv = new StringBuilder();
        csv.append("# AutoAssist — Rapport Analytique\n");
        csv.append("# Période: ").append(period).append(" | Exporté le: ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
        csv.append("# Total tickets: ").append(kpis.getTotalTickets())
           .append(" | Résolution: ").append(kpis.getTauxResolution()).append("%")
           .append(" | Temps moyen: ").append(kpis.getTempsMoyenMinutes()).append("min\n");
        csv.append("\n");
        csv.append("Date,Total Tickets,Nouveaux,En Cours,Résolus,Fermés,Appels,Emails,Web,Décisions Favorable,Décisions Défavorable,Décisions Étude,Temps Moyen (min)\n");

        for (DailyStat s : stats) {
            csv.append(s.getDate()).append(",")
               .append(s.getTotalTickets()).append(",")
               .append(s.getTicketsNouveau()).append(",")
               .append(s.getTicketsEnCours()).append(",")
               .append(s.getTicketsResolus()).append(",")
               .append(s.getTicketsFerme()).append(",")
               .append(s.getTicketsCall()).append(",")
               .append(s.getTicketsEmail()).append(",")
               .append(s.getTicketsWeb()).append(",")
               .append(s.getDecisionsFavorable()).append(",")
               .append(s.getDecisionsDefavorable()).append(",")
               .append(s.getDecisionsEtude()).append(",")
               .append(s.getTempsMoyenMinutes() != null ? s.getTempsMoyenMinutes() : "")
               .append("\n");
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=autoassist_analytics_" + period + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
