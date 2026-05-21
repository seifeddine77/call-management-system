package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.OperatorPerformanceResponse;
import com.pfe.callmgmt.ticket.dto.SupervisionAlert;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service de supervision des opérateurs.
 * Calcule les performances individuelles et détecte les anomalies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisionService {

    private final TicketRepository ticketRepository;

    /**
     * Classement des opérateurs par nombre de tickets traités.
     * Les noms sont résolus dans le controller via OrganisationServiceClient.
     */
    public List<OperatorPerformanceResponse> getOperatorPerformance(String period) {
        LocalDateTime from = resolveDateFrom(period);
        List<Object[]> raw = ticketRepository.findOperatorPerformanceRaw(from);

        // SLA global pour la période
        long slaCompliant = ticketRepository.countSlaCompliant(from);
        long totalResolved = ticketRepository.countResolvedAfter(from);
        double slaGlobal = totalResolved > 0 ? Math.round((double) slaCompliant / totalResolved * 10000.0) / 100.0 : 0;

        List<OperatorPerformanceResponse> result = new ArrayList<>();
        for (Object[] row : raw) {
            UUID opId = row[0] instanceof UUID ? (UUID) row[0] : UUID.fromString(row[0].toString());
            int total = ((Number) row[1]).intValue();
            int enCours = ((Number) row[2]).intValue();
            int resolu = ((Number) row[3]).intValue();
            double tempsMoyen = row[4] instanceof BigDecimal
                    ? ((BigDecimal) row[4]).doubleValue()
                    : ((Number) row[4]).doubleValue();
            int call = ((Number) row[5]).intValue();
            int email = ((Number) row[6]).intValue();
            int web = ((Number) row[7]).intValue();

            double taux = total > 0 ? Math.round((double) resolu / total * 10000.0) / 100.0 : 0;

            // Score composite (0-100) = pondération résolution(40%) + temps(30%) + volume(30%)
            double scoreResolution = Math.min(taux, 100.0);
            double scoreTemps = tempsMoyen <= 0 ? 100 : Math.max(0, 100 - (tempsMoyen / 2.4));
            double scoreVolume = Math.min((total / Math.max(1.0, raw.size())) * 50, 100);
            int scoreComposite = (int) Math.round(scoreResolution * 0.4 + scoreTemps * 0.3 + scoreVolume * 0.3);
            scoreComposite = Math.min(100, Math.max(0, scoreComposite));

            result.add(OperatorPerformanceResponse.builder()
                    .operateurId(opId)
                    .operateurName(null) // Resolved by controller via OrganisationServiceClient
                    .ticketsTraites(total)
                    .ticketsEnCours(enCours)
                    .ticketsResolu(resolu)
                    .tauxResolution(taux)
                    .tempsMoyenMinutes(Math.round(tempsMoyen * 100.0) / 100.0)
                    .ticketsCall(call)
                    .ticketsEmail(email)
                    .ticketsWeb(web)
                    .scoreComposite(scoreComposite)
                    .slaCompliance(slaGlobal)
                    .build());
        }

        log.debug("Supervision: {} operators found for period {}", result.size(), period);
        return result;
    }

    /**
     * Détection d'anomalies — seuils simples sur les données existantes.
     */
    public List<SupervisionAlert> getAlerts() {
        List<SupervisionAlert> alerts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 1. Tickets bloqués — EN_COURS depuis > 24h
        long blocked24h = ticketRepository.countBlockedTickets(now.minusHours(24));
        if (blocked24h > 0) {
            alerts.add(SupervisionAlert.builder()
                    .type(SupervisionAlert.AlertType.TICKET_BLOQUE)
                    .severity(blocked24h >= 5
                            ? SupervisionAlert.Severity.CRITIQUE
                            : SupervisionAlert.Severity.WARNING)
                    .message(blocked24h + " ticket(s) en cours depuis plus de 24h")
                    .count((int) blocked24h)
                    .build());
        }

        // 2. Tickets non assignés depuis > 2h
        long unassigned = ticketRepository.countOldUnassignedTickets(now.minusHours(2));
        if (unassigned > 0) {
            alerts.add(SupervisionAlert.builder()
                    .type(SupervisionAlert.AlertType.NON_ASSIGNE)
                    .severity(unassigned >= 10
                            ? SupervisionAlert.Severity.CRITIQUE
                            : SupervisionAlert.Severity.WARNING)
                    .message(unassigned + " ticket(s) non assigné(s) depuis plus de 2h")
                    .count((int) unassigned)
                    .build());
        }

        // 3. Volume spike — tickets aujourd'hui vs moyenne 7 derniers jours
        long todayCount = ticketRepository.countCreatedAfter(now.toLocalDate().atStartOfDay());
        long weekCount = ticketRepository.countCreatedAfter(now.minusDays(7));
        double dailyAvg = weekCount / 7.0;
        if (dailyAvg > 0 && todayCount > dailyAvg * 2) {
            alerts.add(SupervisionAlert.builder()
                    .type(SupervisionAlert.AlertType.VOLUME_SPIKE)
                    .severity(SupervisionAlert.Severity.WARNING)
                    .message("Volume anormal : " + todayCount + " tickets aujourd'hui (moy. " +
                            Math.round(dailyAvg) + "/jour)")
                    .count((int) todayCount)
                    .build());
        }

        // 4. SLA compliance — alerte si < 70%
        long slaOk = ticketRepository.countSlaCompliant(now.minusDays(7));
        long totalResolved = ticketRepository.countResolvedAfter(now.minusDays(7));
        if (totalResolved > 0) {
            double slaPct = (double) slaOk / totalResolved * 100;
            if (slaPct < 70) {
                alerts.add(SupervisionAlert.builder()
                        .type(SupervisionAlert.AlertType.OPERATEUR_SURCHARGE)
                        .severity(slaPct < 50 ? SupervisionAlert.Severity.CRITIQUE : SupervisionAlert.Severity.WARNING)
                        .message("SLA < " + Math.round(slaPct) + "% — " + (totalResolved - slaOk) +
                                " ticket(s) résolus en plus de 4h")
                        .count((int) (totalResolved - slaOk))
                        .build());
            }
        }

        // 5. Si tout va bien — message positif
        if (alerts.isEmpty()) {
            alerts.add(SupervisionAlert.builder()
                    .type(SupervisionAlert.AlertType.VOLUME_SPIKE)
                    .severity(SupervisionAlert.Severity.INFO)
                    .message("Tout est normal — aucune anomalie détectée")
                    .count(0)
                    .build());
        }

        log.debug("Supervision: {} alert(s) generated", alerts.size());
        return alerts;
    }

    private LocalDateTime resolveDateFrom(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period != null ? period : "7d") {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "7d", "week" -> now.minusDays(7);
            case "30d", "month" -> now.minusDays(30);
            case "90d", "year" -> now.minusDays(90);
            default -> now.minusDays(7);
        };
    }
}
