package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketActionResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String action;
    private String oldValue;
    private String newValue;
    private String notes;
    private LocalDateTime createdAt;
}
