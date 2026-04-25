package com.pfe.callmgmt.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private String role;
    private UUID agenceId;
    private String agenceName;
    private String planning;
    private boolean actif;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
}
