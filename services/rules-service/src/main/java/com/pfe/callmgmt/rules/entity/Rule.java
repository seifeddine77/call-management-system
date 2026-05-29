package com.pfe.callmgmt.rules.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entité Rule — Règle d'assurance (CDC Module 4)
 * Les conditions sont stockées en JSONB PostgreSQL pour flexibilité maximale.
 */
@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Conditions JSONB. Format:
     * {
     * "operator": "AND" | "OR",
     * "conditions": [
     * {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
     * {"field": "ageVehiculeAns", "op": "LT", "value": 5}
     * ]
     * }
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> conditions;

    @Column(nullable = false, length = 15)
    private String action; // FAVORABLE, DEFAVORABLE, ETUDE

    @Column(name = "action_motif", nullable = false, columnDefinition = "TEXT")
    private String actionMotif;

    @Column(nullable = false)
    @Builder.Default
    private Short priorite = 5;

    @Column(name = "score_impact", nullable = false)
    @Builder.Default
    private Short scoreImpact = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(nullable = false)
    @Builder.Default
    private Short version = 1;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
