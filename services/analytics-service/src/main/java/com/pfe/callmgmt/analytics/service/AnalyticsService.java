package com.pfe.callmgmt.analytics.service;

import com.pfe.callmgmt.analytics.dto.HourlyData;
import com.pfe.callmgmt.analytics.dto.KpiComparison;
import com.pfe.callmgmt.analytics.dto.KpiResponse;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.repository.DailyStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final DailyStatRepository dailyStatRepository;

    /**
     * Calcule les KPIs agrégés pour une période donnée.
     * Utilise les vrais compteurs par statut au lieu d'estimations dérivées.
     */
    public KpiResponse getKpis(String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = resolvePeriodStart(period, to);
        return getKpisForRange(from, to, period);
    }

    /**
     * Comparaison période-sur-période.
     * Retourne les KPIs actuels, les KPIs de la période précédente, et les deltas en %.
     */
    public KpiComparison getComparison(String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = resolvePeriodStart(period, to);
        long days = ChronoUnit.DAYS.between(from, to);

        // Période précédente de même durée
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days);

        KpiResponse current = getKpisForRange(from, to, period);
        KpiResponse previous = getKpisForRange(prevFrom, prevTo, period);

        return KpiComparison.builder()
                .current(current)
                .previous(previous)
                .deltaTickets(pctChange(previous.getTotalTickets(), current.getTotalTickets()))
                .deltaResolution(pctChange(previous.getTauxResolution(), current.getTauxResolution()))
                .deltaTempsMoyen(pctChange(previous.getTempsMoyenMinutes(), current.getTempsMoyenMinutes()))
                .deltaDecisions(pctChange(
                        previous.getDecisionsAiFavorable() + previous.getDecisionsAiDefavorable() + previous.getDecisionsAiEtude(),
                        current.getDecisionsAiFavorable() + current.getDecisionsAiDefavorable() + current.getDecisionsAiEtude()))
                .deltaFavorable(pctChange(previous.getDecisionsAiFavorable(), current.getDecisionsAiFavorable()))
                .build();
    }

    // ========== Calcul KPI pour une plage de dates ==========

    private KpiResponse getKpisForRange(LocalDate from, LocalDate to, String period) {
        List<DailyStat> stats = dailyStatRepository.findByDateBetweenOrderByDateDesc(from, to);

        int totalTickets = stats.stream().mapToInt(DailyStat::getTotalTickets).sum();
        int ticketsResolus = stats.stream().mapToInt(DailyStat::getTicketsResolus).sum();
        int ticketsNouveau = stats.stream().mapToInt(DailyStat::getTicketsNouveau).sum();
        int ticketsEnCours = stats.stream().mapToInt(DailyStat::getTicketsEnCours).sum();
        int ticketsFerme = stats.stream().mapToInt(DailyStat::getTicketsFerme).sum();
        int decFavorable = stats.stream().mapToInt(DailyStat::getDecisionsFavorable).sum();
        int decDefavorable = stats.stream().mapToInt(DailyStat::getDecisionsDefavorable).sum();
        int decEtude = stats.stream().mapToInt(DailyStat::getDecisionsEtude).sum();
        int ticketsCall = stats.stream().mapToInt(DailyStat::getTicketsCall).sum();
        int ticketsEmail = stats.stream().mapToInt(DailyStat::getTicketsEmail).sum();
        int ticketsWeb = stats.stream().mapToInt(DailyStat::getTicketsWeb).sum();

        // Temps moyen pondéré — hybrid V1+V2
        BigDecimal totalResMin = BigDecimal.ZERO;
        int totalResCount = 0;
        for (DailyStat s : stats) {
            int v2Count = s.getResolvedCountToday() != null ? s.getResolvedCountToday() : 0;
            BigDecimal v2Total = s.getTotalResolutionMinutes() != null ? s.getTotalResolutionMinutes() : BigDecimal.ZERO;

            if (v2Count > 0 && v2Total.compareTo(BigDecimal.ZERO) > 0) {
                totalResMin = totalResMin.add(v2Total);
                totalResCount += v2Count;
            } else if (s.getTempsMoyenMinutes() != null && s.getTempsMoyenMinutes().compareTo(BigDecimal.ZERO) > 0
                    && s.getTicketsResolus() > 0) {
                totalResMin = totalResMin.add(s.getTempsMoyenMinutes().multiply(BigDecimal.valueOf(s.getTicketsResolus())));
                totalResCount += s.getTicketsResolus();
            }
        }
        double tempsMoyen = totalResCount > 0
                ? totalResMin.divide(BigDecimal.valueOf(totalResCount), 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        double tauxResolution = totalTickets > 0
                ? (double) ticketsResolus / totalTickets * 100.0 : 0.0;

        return KpiResponse.builder()
                .totalTickets(totalTickets)
                .ticketsNouveau(ticketsNouveau)
                .ticketsEnCours(ticketsEnCours)
                .ticketsResolu(ticketsResolus)
                .ticketsFerme(ticketsFerme)
                .tauxResolution(Math.round(tauxResolution * 100.0) / 100.0)
                .tempsMoyenMinutes(Math.round(tempsMoyen * 100.0) / 100.0)
                .decisionsAiFavorable(decFavorable)
                .decisionsAiDefavorable(decDefavorable)
                .decisionsAiEtude(decEtude)
                .ticketsCall(ticketsCall)
                .ticketsEmail(ticketsEmail)
                .ticketsWeb(ticketsWeb)
                .period(period)
                .build();
    }

    private double pctChange(double prev, double curr) {
        if (prev == 0) return curr > 0 ? 100.0 : 0.0;
        return Math.round(((curr - prev) / prev) * 10000.0) / 100.0;
    }

    /**
     * Retourne les tickets par jour pour les graphiques.
     */
    public List<HourlyData> getTicketsPerDay(String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = resolvePeriodStart(period, to);

        List<DailyStat> stats = dailyStatRepository.findByDateBetweenOrderByDateDesc(from, to);
        List<HourlyData> result = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (DailyStat stat : stats) {
            result.add(HourlyData.builder()
                    .label(stat.getDate().format(fmt))
                    .count(stat.getTotalTickets())
                    .build());
        }
        return result;
    }

    /**
     * Retourne les statistiques quotidiennes brutes.
     */
    public List<DailyStat> getDailyStats(String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = resolvePeriodStart(period, to);
        return dailyStatRepository.findByDateBetweenOrderByDateDesc(from, to);
    }

    /**
     * Incrémente les stats du jour pour un ticket créé.
     * Un ticket créé est toujours NOUVEAU au départ.
     */
    @Transactional
    public void incrementTicketCreated(String canal) {
        DailyStat stat = getOrCreateToday();
        stat.setTotalTickets(stat.getTotalTickets() + 1);
        stat.setTicketsNouveau(stat.getTicketsNouveau() + 1);
        switch (canal != null ? canal.toUpperCase() : "") {
            case "CALL" -> stat.setTicketsCall(stat.getTicketsCall() + 1);
            case "EMAIL" -> stat.setTicketsEmail(stat.getTicketsEmail() + 1);
            case "WEB" -> stat.setTicketsWeb(stat.getTicketsWeb() + 1);
            default -> log.debug("Canal inconnu ignoré: {}", canal);
        }
        dailyStatRepository.save(stat);
        log.debug("📊 DailyStat updated: ticket created (canal={})", canal);
    }

    /**
     * Incrémente les stats du jour pour un ticket résolu.
     * Calcule la durée de résolution si ticketCreatedAt est fourni.
     *
     * @param ticketCreatedAt timestamp de création du ticket (peut être null pour rétro-compatibilité)
     */
    @Transactional
    public void incrementTicketResolved(LocalDateTime ticketCreatedAt) {
        DailyStat stat = getOrCreateToday();
        stat.setTicketsResolus(stat.getTicketsResolus() + 1);

        // Calcul du temps de résolution et mise à jour de la moyenne pondérée
        if (ticketCreatedAt != null) {
            long durationMinutes = ChronoUnit.MINUTES.between(ticketCreatedAt, LocalDateTime.now());
            durationMinutes = Math.max(0, durationMinutes); // Protection contre les valeurs négatives

            BigDecimal currentTotal = stat.getTotalResolutionMinutes() != null
                    ? stat.getTotalResolutionMinutes() : BigDecimal.ZERO;
            stat.setTotalResolutionMinutes(currentTotal.add(BigDecimal.valueOf(durationMinutes)));

            int currentCount = stat.getResolvedCountToday() != null ? stat.getResolvedCountToday() : 0;
            stat.setResolvedCountToday(currentCount + 1);

            // Recalculer le temps moyen du jour
            BigDecimal newTotal = stat.getTotalResolutionMinutes();
            int newCount = stat.getResolvedCountToday();
            stat.setTempsMoyenMinutes(newTotal.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP));

            log.debug("📊 Resolution time: {} minutes (avg today: {} min)", durationMinutes, stat.getTempsMoyenMinutes());
        }

        dailyStatRepository.save(stat);
        log.debug("📊 DailyStat updated: ticket resolved");
    }

    /**
     * Met à jour les compteurs de statut quand un ticket change de statut.
     * Incrémente le nouveau statut, décrémente l'ancien.
     *
     * @param oldStatut ancien statut (NOUVEAU, EN_COURS, RESOLU, FERME)
     * @param newStatut nouveau statut
     */
    @Transactional
    public void onTicketStatusChanged(String oldStatut, String newStatut) {
        DailyStat stat = getOrCreateToday();

        // Décrémenter l'ancien statut (avec protection contre les négatifs)
        decrementStatutCounter(stat, oldStatut);
        // Incrémenter le nouveau statut
        incrementStatutCounter(stat, newStatut);

        dailyStatRepository.save(stat);
        log.debug("📊 DailyStat updated: status {} → {}", oldStatut, newStatut);
    }

    /**
     * Incrémente les stats du jour pour une décision finalisée.
     */
    @Transactional
    public void incrementDecision(String finalDecision) {
        DailyStat stat = getOrCreateToday();
        switch (finalDecision != null ? finalDecision.toUpperCase() : "") {
            case "FAVORABLE" -> stat.setDecisionsFavorable(stat.getDecisionsFavorable() + 1);
            case "DEFAVORABLE" -> stat.setDecisionsDefavorable(stat.getDecisionsDefavorable() + 1);
            case "ETUDE" -> stat.setDecisionsEtude(stat.getDecisionsEtude() + 1);
            default -> log.debug("Décision inconnue ignorée: {}", finalDecision);
        }
        dailyStatRepository.save(stat);
        log.debug("📊 DailyStat updated: decision {}", finalDecision);
    }

    /**
     * Agrégation nocturne — vérifie que la stat du jour existe.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void nightlyAggregation() {
        getOrCreateToday();
        log.info("📊 Nightly aggregation check completed for {}", LocalDate.now());
    }

    // ========== Helpers privés ==========

    private DailyStat getOrCreateToday() {
        LocalDate today = LocalDate.now();
        return dailyStatRepository.findByDate(today)
                .orElseGet(() -> dailyStatRepository.save(DailyStat.builder().date(today).build()));
    }

    private LocalDate resolvePeriodStart(String period, LocalDate to) {
        return switch (period) {
            case "today" -> to;
            case "7d", "week" -> to.minusDays(7);
            case "30d", "month" -> to.minusDays(30);
            case "90d", "year" -> to.minusDays(90);
            default -> to.minusDays(7);
        };
    }

    private void incrementStatutCounter(DailyStat stat, String statut) {
        if (statut == null) return;
        switch (statut.toUpperCase()) {
            case "NOUVEAU" -> stat.setTicketsNouveau(stat.getTicketsNouveau() + 1);
            case "EN_COURS" -> stat.setTicketsEnCours(stat.getTicketsEnCours() + 1);
            case "RESOLU" -> { /* Géré par incrementTicketResolved */ }
            case "FERME" -> stat.setTicketsFerme(stat.getTicketsFerme() + 1);
            default -> log.debug("Statut inconnu ignoré (increment): {}", statut);
        }
    }

    private void decrementStatutCounter(DailyStat stat, String statut) {
        if (statut == null) return;
        switch (statut.toUpperCase()) {
            case "NOUVEAU" -> stat.setTicketsNouveau(Math.max(0, stat.getTicketsNouveau() - 1));
            case "EN_COURS" -> stat.setTicketsEnCours(Math.max(0, stat.getTicketsEnCours() - 1));
            case "RESOLU" -> { /* Ne pas décrémenter ticketsResolus ici */ }
            case "FERME" -> stat.setTicketsFerme(Math.max(0, stat.getTicketsFerme() - 1));
            default -> log.debug("Statut inconnu ignoré (decrement): {}", statut);
        }
    }
}
