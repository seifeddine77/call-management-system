package com.pfe.callmgmt.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table locale des utilisateurs dans auth_db.
 * Sert de fallback quand organisation-service est indisponible.
 * Sera synchronisée avec organisation-service une fois implémenté.
 */
@Entity
@Table(name = "local_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nom;

    @Column(nullable = false, length = 50)
    private String prenom;

    private String telephone;

    @Column(nullable = false)
    private String role;

    @Column(name = "agence_id")
    private UUID agenceId;

    @Builder.Default
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
