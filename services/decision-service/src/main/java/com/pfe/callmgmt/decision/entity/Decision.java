package com.pfe.callmgmt.decision.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decisions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "ticket_id", unique = true, nullable = false)
    private UUID ticketId;

    @Column(nullable = false, length = 15)
    private String decision; // FAVORABLE, DEFAVORABLE, ETUDE

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    @Column(nullable = false, length = 10)
    private String source; // RULES, AI, HYBRID

    @Type(JsonType.class)
    @Column(name = "rules_applied", columnDefinition = "TEXT")
    private List<String> rulesApplied;

    @Column(name = "final_decision", length = 15)
    private String finalDecision; // FAVORABLE, DEFAVORABLE (choix opérateur)

    @Column(name = "override_motif", columnDefinition = "TEXT")
    private String overrideMotif;

    @Column(name = "override_by")
    private UUID overrideBy;

    @Column(name = "override_by_name", length = 100)
    private String overrideByName;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
