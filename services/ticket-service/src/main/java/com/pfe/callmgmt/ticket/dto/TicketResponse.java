package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private String numero;
    private String clientNom;
    private String clientTel;
    private String clientEmail;
    private String canal;
    private String statut;
    private Short priorite;
    private UUID operateurId;
    private String operateurName;
    private UUID agenceId;
    private String agenceName;
    private String description;
    private String audioPath;
    private String transcription;
    private String transcriptionKeywords;
    private String photosPaths;
    private Boolean consentementEnregistrement;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketActionResponse> actions;
}
