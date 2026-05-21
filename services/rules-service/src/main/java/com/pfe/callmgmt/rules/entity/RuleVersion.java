package com.pfe.callmgmt.rules.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entité RuleVersion — Historique des versions d'une règle (CDC 4.D)
 * Chaque modification d'une règle crée une copie de l'ancienne version ici.
 */
@Entity
@Table(name = "rule_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> conditions;

    @Column(length = 15)
    private String action;

    @Column(name = "action_motif", columnDefinition = "TEXT")
    private String actionMotif;

    private Short priorite;

    @Column(nullable = false)
    private Short version;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_by_name", length = 100)
    private String changedByName;

    @Column(name = "changed_at")
    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();
}
