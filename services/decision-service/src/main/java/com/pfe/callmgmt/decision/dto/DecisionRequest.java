package com.pfe.callmgmt.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {

    @NotNull(message = "Le ticketId est obligatoire")
    private UUID ticketId;

    @NotBlank(message = "Le type de pièce est obligatoire")
    private String typePiece;

    @NotNull(message = "L'âge du véhicule est obligatoire")
    private Integer ageVehiculeAns;

    @NotNull(message = "Le kilométrage est obligatoire")
    private Integer kilometrage;

    @NotBlank(message = "La catégorie client est obligatoire")
    private String categorieClient;

    @NotBlank(message = "Le type de sinistre est obligatoire")
    private String typeSinistre;

    private String transcriptionText;
    private String pieceReference;
}

