package com.pfe.callmgmt.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la création/modification d'un élément de référence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceItemRequest {

    @NotBlank(message = "La catégorie est obligatoire")
    @Size(max = 50)
    private String category;

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 100)
    private String code;

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 200)
    private String label;

    private String description;

    private Integer displayOrder;
}
