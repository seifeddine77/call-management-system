package com.pfe.callmgmt.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mirrors rules-service EvaluateResponse received via Feign.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulesEvaluateResponse {

    private String decision;
    private Double score;
    private String motif;
    private List<String> rulesApplied;
    private Integer totalRulesEvaluated;
    private Integer matchedRules;
    private Boolean ambiguous;
}
