package com.pfe.callmgmt.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de réponse pour une règle complète.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResponse {
    private UUID id;
    private String nom;
    private String description;
    private Map<String, Object> conditions;
    private String action;
    private String actionMotif;
    private Short priorite;
    private Boolean actif;
    private Short version;
    private UUID createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
