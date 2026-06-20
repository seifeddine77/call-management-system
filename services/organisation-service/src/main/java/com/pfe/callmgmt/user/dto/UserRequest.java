package com.pfe.callmgmt.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @Size(min = 6, max = 100, message = "Le mot de passe doit faire entre 6 et 100 caractères")
    private String password; // nullable on update, may contain BCrypt hash from auth-service

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50, message = "Le nom ne peut pas dépasser 50 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 50, message = "Le prénom ne peut pas dépasser 50 caractères")
    private String prenom;

    private String telephone;

    @NotBlank(message = "Le rôle est obligatoire")
    @Pattern(regexp = "ADMIN|OPERATOR|SUPERVISOR", message = "Le rôle doit être ADMIN, OPERATOR ou SUPERVISOR")
    private String role;

    private UUID agenceId;

    private String planning; // Horaires de travail (ex: "Lundi-Vendredi 08:00-17:00")
}
