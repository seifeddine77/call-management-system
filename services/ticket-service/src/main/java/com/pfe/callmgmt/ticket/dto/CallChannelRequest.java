package com.pfe.callmgmt.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO pour la réception d'un appel VoIP (webhook Asterisk/Twilio).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallChannelRequest {

    @NotBlank(message = "Le numéro de l'appelant est obligatoire")
    private String callerNumber;

    private String callerName;

    @NotNull(message = "L'agence est obligatoire")
    private UUID agenceId;

    /** URL de l'enregistrement audio (optionnel, fourni par Asterisk/Twilio) */
    private String audioUrl;

    /** Email de l'appelant (optionnel, pour envoi de notifications) */
    private String callerEmail;

    private String description;

    /** Priorité (1=urgente, 5=basse). Par défaut 3. */
    private Short priorite;
}
