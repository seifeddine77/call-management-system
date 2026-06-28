package com.pfe.callmgmt.analytics.service;

import com.pfe.callmgmt.analytics.client.TicketServiceClient;
import com.pfe.callmgmt.analytics.dto.DailyTicketCount;
import com.pfe.callmgmt.analytics.dto.ReconciliationDrift;
import com.pfe.callmgmt.analytics.entity.DailyStat;
import com.pfe.callmgmt.analytics.repository.DailyStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Réconciliation des KPIs — défense en profondeur contre les pertes d'événements.
 *
 * <p>Les compteurs de DailyStat sont une <b>projection</b> alimentée par les événements
 * RabbitMQ. Si un événement est perdu (broker indisponible à l'émission, erreur de
 * traitement avalée par le listener…), la projection peut sous-compter. Comme la
 * <b>source de vérité</b> reste la base du ticket-service, on peut détecter et corriger
 * cette dérive sans perte de donnée métier.</p>
 *
 * <p>Périmètre volontairement limité aux <b>faits de création</b> (total + ventilation
 * par canal), qui se mappent sans ambiguïté sur « tickets créés ce jour-là ». Les champs
 * de résolution, de distribution par statut et de décision ne sont <b>pas</b> touchés :
 * leur sémantique (compteurs mutés au fil des événements, attribués au jour de l'événement)
 * n'est pas reconstructible par jour de création, et les écraser fausserait les KPIs.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final TicketServiceClient ticketServiceClient;
    private final DailyStatRepository dailyStatRepository;

    /** Correction automatique lors de la passe nocturne. false = détection seule (sûr par défaut). */
    @Value("${analytics.reconciliation.auto-correct:false}")
    private boolean autoCorrect;

    /** Fenêtre glissante réconciliée par la passe nocturne (en jours). */
    @Value("${analytics.reconciliation.window-days:30}")
    private int windowDays;

    /**
     * Passe nocturne (1h30, après l'agrégation de 1h00).
     * Ne fait jamais échouer le scheduler : si le ticket-service est indisponible,
     * on loggue et on réessaiera la nuit suivante (ou via l'endpoint manuel).
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void scheduledReconciliation() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(windowDays);
        try {
            List<ReconciliationDrift> drifts = reconcile(from, to, autoCorrect);
            if (drifts.isEmpty()) {
                log.info("✅ Réconciliation: aucune dérive ({} → {})", from, to);
            } else {
                log.warn("⚠️ Réconciliation: {} écart(s) détecté(s) sur {} → {} (auto-correct={})",
                        drifts.size(), from, to, autoCorrect);
            }
        } catch (Exception e) {
            log.error("❌ Réconciliation nocturne échouée (ticket-service indisponible ?): {}", e.getMessage(), e);
        }
    }

    /**
     * Compare les faits de création entre la source de vérité et DailyStat.
     *
     * @param apply si true, corrige les écarts (champs de création uniquement) ;
     *              sinon détection seule (aucune écriture).
     * @return la liste des écarts détectés (chacun marqué corrected=apply).
     */
    @Transactional
    public List<ReconciliationDrift> reconcile(LocalDate from, LocalDate to, boolean apply) {
        List<DailyTicketCount> truth = ticketServiceClient.getDailyTicketCounts(from, to);
        List<ReconciliationDrift> drifts = new ArrayList<>();

        for (DailyTicketCount t : truth) {
            reconcileSingleDate(t, apply, drifts);
        }
        return drifts;
    }

    private void reconcileSingleDate(DailyTicketCount t, boolean apply, List<ReconciliationDrift> drifts) {
        DailyStat stat = dailyStatRepository.findByDate(t.getDate()).orElse(null);

        long actualTotal = stat != null ? nz(stat.getTotalTickets()) : 0;
        long actualCall = stat != null ? nz(stat.getTicketsCall()) : 0;
        long actualEmail = stat != null ? nz(stat.getTicketsEmail()) : 0;
        long actualWeb = stat != null ? nz(stat.getTicketsWeb()) : 0;

        boolean hasDrift = actualTotal != t.getTotalTickets()
                || actualCall != t.getTicketsCall()
                || actualEmail != t.getTicketsEmail()
                || actualWeb != t.getTicketsWeb();
        if (!hasDrift) {
            return;
        }

        if (apply) {
            if (stat == null) {
                stat = DailyStat.builder().date(t.getDate()).build();
            }
            stat.setTotalTickets((int) t.getTotalTickets());
            stat.setTicketsCall((int) t.getTicketsCall());
            stat.setTicketsEmail((int) t.getTicketsEmail());
            stat.setTicketsWeb((int) t.getTicketsWeb());
            dailyStatRepository.save(stat);
        }

        recordDrift(drifts, t.getDate(), "totalTickets", t.getTotalTickets(), actualTotal, apply);
        recordDrift(drifts, t.getDate(), "ticketsCall", t.getTicketsCall(), actualCall, apply);
        recordDrift(drifts, t.getDate(), "ticketsEmail", t.getTicketsEmail(), actualEmail, apply);
        recordDrift(drifts, t.getDate(), "ticketsWeb", t.getTicketsWeb(), actualWeb, apply);
    }


    private void recordDrift(List<ReconciliationDrift> drifts, LocalDate date, String field,
                             long expected, long actual, boolean corrected) {
        if (expected == actual) {
            return;
        }
        drifts.add(ReconciliationDrift.builder()
                .date(date).field(field).expected(expected).actual(actual).corrected(corrected).build());
        log.warn("📊 Dérive {} le {}: vérité={}, projeté={}{}",
                field, date, expected, actual, corrected ? " → corrigé" : "");
    }

    private long nz(Integer v) {
        return v != null ? v : 0;
    }
}
