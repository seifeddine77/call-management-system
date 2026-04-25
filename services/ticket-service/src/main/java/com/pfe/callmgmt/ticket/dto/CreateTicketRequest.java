package com.pfe.callmgmt.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    @NotBlank(message = "Le nom du client est obligatoire")
    private String clientNom;

    private String clientTel;

    private String clientEmail;

    @NotBlank(message = "Le canal est obligatoire")
    private String canal;

    @NotNull(message = "L'agence est obligatoire")
    private UUID agenceId;

    private String description;

    private Short priorite;

    /** RGPD — Consentement du client à l'enregistrement de l'appel */
    private Boolean consentementEnregistrement;
}
