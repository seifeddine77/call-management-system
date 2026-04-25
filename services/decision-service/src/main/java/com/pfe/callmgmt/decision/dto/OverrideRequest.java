package com.pfe.callmgmt.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverrideRequest {

    @NotBlank(message = "La décision finale est obligatoire")
    @Pattern(regexp = "FAVORABLE|DEFAVORABLE", message = "La décision finale doit être FAVORABLE ou DEFAVORABLE")
    private String finalDecision;

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;

    private UUID operateurId;
    private String operateurName;
    private String clientNom;
    private String clientEmail;
}
