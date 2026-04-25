package com.pfe.callmgmt.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.util.Map;

/**
 * DTO de création d'une règle d'assurance.
 */
@Data
public class CreateRuleRequest {

    @NotBlank(message = "Le nom de la règle est obligatoire")
    @Size(max = 200, message = "Le nom ne peut pas dépasser 200 caractères")
    private String nom;

    private String description;

    @NotNull(message = "Les conditions sont obligatoires")
    private Map<String, Object> conditions;

    @NotBlank(message = "L'action est obligatoire (FAVORABLE, DEFAVORABLE, ETUDE)")
    private String action;

    @NotBlank(message = "Le motif de l'action est obligatoire")
    private String actionMotif;

    @Range(min = 1, max = 10, message = "La priorité doit être entre 1 et 10")
    private Short priorite = 5;
}
