package com.pfe.callmgmt.analytics.client;

import com.pfe.callmgmt.analytics.dto.DailyTicketCount;
import com.pfe.callmgmt.analytics.dto.TicketStatsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Client Feign vers le ticket-service (résolu via Eureka).
 * Lecture seule — récupère la source de vérité des créations de tickets
 * pour réconcilier les KPIs analytics.
 */
@FeignClient(name = "ticket-service")
public interface TicketServiceClient {

    @GetMapping("/api/tickets/stats/daily-counts")
    List<DailyTicketCount> getDailyTicketCounts(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    /** Ventilation par statut courant des tickets créés sur la plage — pour un KPI de statut cohérent. */
    @GetMapping("/api/tickets/stats/by-period")
    TicketStatsDto getStatsByPeriod(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
