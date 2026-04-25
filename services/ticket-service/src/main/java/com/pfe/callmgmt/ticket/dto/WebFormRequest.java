package com.pfe.callmgmt.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO pour la réception d'un formulaire web public.
 * Endpoint accessible sans authentification (site web / mobile).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebFormRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String telephone;

    private String email;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "L'agence est obligatoire")
    private UUID agenceId;

    /** Priorité (1=urgente, 5=basse). Par défaut 3. */
    private Short priorite;

    /** RGPD — Consentement au traitement des données personnelles */
    private Boolean consentementTraitement;
}
