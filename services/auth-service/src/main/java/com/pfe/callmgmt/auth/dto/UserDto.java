package com.pfe.callmgmt.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

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
