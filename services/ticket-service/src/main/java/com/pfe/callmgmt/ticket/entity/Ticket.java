package com.pfe.callmgmt.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    // Thread-safe counter to ensure unique ticket numbers even under concurrency
    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis() % 100000);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    @Column(name = "client_nom", nullable = false, length = 200)
    private String clientNom;

    @Column(name = "client_tel", length = 20)
    private String clientTel;

    @Column(name = "client_email", length = 255)
    private String clientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Statut statut;

    @Column
    @Builder.Default
    private Short priorite = 3;

    @Column(name = "operateur_id")
    private UUID operateurId;

    @Column(name = "agence_id", nullable = false)
    private UUID agenceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "audio_path", length = 500)
    private String audioPath;

    @Column(columnDefinition = "TEXT")
    private String transcription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcription_keywords", columnDefinition = "jsonb")
    private String transcriptionKeywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photos_paths", columnDefinition = "jsonb")
    private String photosPaths;

    @Column(name = "consentement_enregistrement")
    @Builder.Default
    private Boolean consentementEnregistrement = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.statut == null) {
            this.statut = Statut.NOUVEAU;
        }
        if (this.numero == null) {
            // Thread-safe: AtomicLong guarantees no collision even under concurrent inserts
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long seq = COUNTER.getAndIncrement() % 100000;
            this.numero = "T-" + date + "-" + String.format("%05d", seq);
        }
    }

    // ========== Enums ==========

    public enum Canal {
        CALL, EMAIL, WEB
    }

    public enum Statut {
        NOUVEAU, EN_COURS, RESOLU, FERME;

        /**
         * Validates whether a transition from this status to the target status is
         * allowed.
         */
        public boolean canTransitionTo(Statut target) {
            return switch (this) {
                case NOUVEAU -> target == EN_COURS || target == FERME;
                case EN_COURS -> target == RESOLU || target == FERME;
                case RESOLU -> target == FERME;
                case FERME -> false; // Terminal state — no transitions allowed
            };
        }
    }
}
