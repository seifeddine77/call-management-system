package com.pfe.callmgmt.notification.event;

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
    private String clientNom;
    private String clientEmail;
    private String finalDecision;
    private String motif;
    private String letterFilePath;
}
