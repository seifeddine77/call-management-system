package com.pfe.callmgmt.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatutRequest {

    @NotBlank(message = "Le nouveau statut est obligatoire")
    private String newStatut;

    private String notes;
}
