package com.pfe.callmgmt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgenceRequest {

    @NotBlank(message = "Le nom de l'agence est obligatoire")
    @Size(max = 200, message = "Le nom ne peut pas dépasser 200 caractères")
    private String nom;

    private String adresse;
    private String telephone;
    private String email;
    private String stockDescription; // Description du stock de l'agence
}
