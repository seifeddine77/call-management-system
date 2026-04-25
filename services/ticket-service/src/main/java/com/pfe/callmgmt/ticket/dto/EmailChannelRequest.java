package com.pfe.callmgmt.ticket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO pour la réception d'un email parsé (intégration IMAP).
 * Simule le parsing automatique d'un email entrant.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailChannelRequest {

    @NotBlank(message = "L'adresse email de l'expéditeur est obligatoire")
    @Email(message = "Format email invalide")
    private String from;

    @NotBlank(message = "Le sujet de l'email est obligatoire")
    private String subject;

    @NotBlank(message = "Le corps de l'email est obligatoire")
    private String body;

    @NotNull(message = "L'agence est obligatoire")
    private UUID agenceId;

    /** Nom de l'expéditeur extrait du header email */
    private String senderName;

    /** Priorité (1=urgente, 5=basse). Par défaut 3. */
    private Short priorite;
}
