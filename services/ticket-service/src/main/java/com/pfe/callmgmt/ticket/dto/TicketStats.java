package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStats {
    private long nouveau;
    private long enCours;
    private long resolu;
    private long ferme;
    private long total;
}
