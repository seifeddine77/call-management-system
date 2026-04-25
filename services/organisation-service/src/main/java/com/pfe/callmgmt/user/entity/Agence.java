package com.pfe.callmgmt.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    @Column(length = 20)
    private String telephone;

    @Column(length = 255)
    private String email;

    @Column(name = "responsable_id")
    private UUID responsableId;

    @Column(name = "stock_description", columnDefinition = "TEXT")
    private String stockDescription; // Description du stock de l'agence

    @Column(name = "whatsapp_group_id", length = 100)
    private String whatsappGroupId; // ID du groupe WhatsApp lié à cette agence

    @Builder.Default
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
