package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketFilter {
    private String statut;
    private String canal;
    private Short priorite;
    private UUID agenceId;
    private UUID operateurId;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String search;
    private int page = 0;
    private int size = 20;
}
