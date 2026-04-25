package com.pfe.callmgmt.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResult {

    private UUID decisionId;
    private UUID ticketId;
    private String decision; // FAVORABLE, DEFAVORABLE, ETUDE
    private double score; // 0-100
    private String motif;
    private String source; // RULES, AI, HYBRID
    private List<String> rulesApplied;
    private boolean ambiguous;
    private boolean cached;
    private String finalDecision;
    private String overrideMotif;
}
