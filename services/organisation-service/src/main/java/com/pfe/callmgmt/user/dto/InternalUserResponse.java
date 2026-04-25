package com.pfe.callmgmt.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO interne utilisé pour la communication inter-services (auth-service ↔
 * user-service).
 * Contient le passwordHash, contrairement à UserResponse (qui est exposé aux
 * clients).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserResponse {
    private UUID id;
    private String email;
    private String passwordHash;
    private String nom;
    private String prenom;
    private String telephone;
    private String role;
    private UUID agenceId;
    private boolean actif;
}
