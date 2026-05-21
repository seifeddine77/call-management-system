package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisionAlert {

    public enum Severity { CRITIQUE, WARNING, INFO }
    public enum AlertType { TICKET_BLOQUE, VOLUME_SPIKE, NON_ASSIGNE, OPERATEUR_SURCHARGE }

    private AlertType type;
    private Severity severity;
    private String message;
    private int count;
}
