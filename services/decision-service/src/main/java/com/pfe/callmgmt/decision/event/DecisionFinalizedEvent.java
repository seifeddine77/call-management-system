package com.pfe.callmgmt.decision.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFinalizedEvent {

    private UUID decisionId;
    private UUID ticketId;
    private String clientNom;
    private String clientEmail;
    private String finalDecision; // FAVORABLE or DEFAVORABLE
    private String motif;
    private String letterFilePath; // PDF path if generated
}
