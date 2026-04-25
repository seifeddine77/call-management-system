package com.pfe.callmgmt.rules.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité ReferenceItem — Élément de liste de référence (CDC 4.B)
 * Stocke les pièces garanties, catégories client, types de sinistre, etc.
 * Administrable via l'interface de paramétrage.
 */
@Entity
@Table(name = "reference_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"category", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String category; // TYPE_PIECE, CATEGORIE_CLIENT, TYPE_SINISTRE

    @Column(nullable = false, length = 100)
    private String code; // MECANIQUE, STANDARD, PANNE...

    @Column(nullable = false, length = 200)
    private String label; // Mécanique, Standard, Panne...

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

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
