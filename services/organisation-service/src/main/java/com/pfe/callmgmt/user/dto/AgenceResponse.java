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
public class AgenceResponse {
    private UUID id;
    private String nom;
    private String adresse;
    private String telephone;
    private String email;
    private UUID responsableId;
    private String responsableName;
    private String stockDescription;
    private String whatsappGroupId;
    private boolean actif;
    private LocalDateTime createdAt;
}
