package com.pfe.callmgmt.analytics.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFinalizedEvent {
    private UUID decisionId;
    private UUID ticketId;
    private String finalDecision; // FAVORABLE, DEFAVORABLE, ETUDE
    private String motif;
}
