package com.pfe.callmgmt.analytics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Builder.Default
    @Column(name = "total_tickets")
    private Integer totalTickets = 0;

    @Builder.Default
    @Column(name = "tickets_resolus")
    private Integer ticketsResolus = 0;

    @Column(name = "temps_moyen_minutes")
    private BigDecimal tempsMoyenMinutes;

    @Builder.Default
    @Column(name = "decisions_favorable")
    private Integer decisionsFavorable = 0;

    @Builder.Default
    @Column(name = "decisions_defavorable")
    private Integer decisionsDefavorable = 0;

    @Builder.Default
    @Column(name = "decisions_etude")
    private Integer decisionsEtude = 0;

    @Builder.Default
    @Column(name = "tickets_call")
    private Integer ticketsCall = 0;

    @Builder.Default
    @Column(name = "tickets_email")
    private Integer ticketsEmail = 0;

    @Builder.Default
    @Column(name = "tickets_web")
    private Integer ticketsWeb = 0;

    @Builder.Default
    @Column(name = "tickets_nouveau")
    private Integer ticketsNouveau = 0;

    @Builder.Default
    @Column(name = "tickets_en_cours")
    private Integer ticketsEnCours = 0;

    @Builder.Default
    @Column(name = "tickets_ferme")
    private Integer ticketsFerme = 0;

    /** Nombre de tickets résolus aujourd'hui (pour calcul moyenne pondérée) */
    @Builder.Default
    @Column(name = "resolved_count_today")
    private Integer resolvedCountToday = 0;

    /** Somme des durées de résolution en minutes (pour calcul tempsMoyenMinutes) */
    @Builder.Default
    @Column(name = "total_resolution_minutes")
    private BigDecimal totalResolutionMinutes = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
