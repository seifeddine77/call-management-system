package com.pfe.callmgmt.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse contenant l'état de la file d'attente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueResponse {

    private long totalEnAttente;
    private List<QueueEntry> tickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueEntry {
        private int position;
        private String ticketNumero;
        private String ticketId;
        private String clientNom;
        private String canal;
        private Short priorite;
        private String createdAt;
    }
}
