package com.pfe.callmgmt.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.util.Map;

/**
 * DTO de modification d'une règle (déclenche le versioning).
 */
@Data
public class UpdateRuleRequest {

    @NotBlank(message = "Le nom de la règle est obligatoire")
    @Size(max = 200, message = "Le nom ne peut pas dépasser 200 caractères")
    private String nom;

    private String description;

    @NotNull(message = "Les conditions sont obligatoires")
    private Map<String, Object> conditions;

    @NotBlank(message = "L'action est obligatoire")
    @Pattern(regexp = "^(FAVORABLE|DEFAVORABLE|ETUDE)$", message = "L'action doit être FAVORABLE, DEFAVORABLE ou ETUDE")
    private String action;

    @NotBlank(message = "Le motif de l'action est obligatoire")
    private String actionMotif;

    @Range(min = 1, max = 10, message = "La priorité doit être entre 1 et 10")
    private Short priorite;

    @NotNull(message = "L'impact de score est obligatoire")
    @Range(min = -50, max = 50, message = "L'impact de score doit être entre -50 et +50")
    private Short scoreImpact;
}
