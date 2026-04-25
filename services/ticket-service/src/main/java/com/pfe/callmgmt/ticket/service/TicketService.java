package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.*;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, UUID operateurId, String operateurName);

    TicketResponse assignTicket(UUID ticketId, UUID operateurId, UUID requesterId, String requesterRole,
            UUID requesterAgenceId);

    TicketResponse updateStatut(UUID ticketId, UpdateStatutRequest request, UUID userId, String userName);

    Page<TicketResponse> getTickets(TicketFilter filter, String userRole, UUID userId, UUID userAgenceId);

    TicketResponse getTicketById(UUID id, String userRole, UUID userId, UUID userAgenceId);

    void updateAudioTranscription(UUID ticketId, String audioPath, String transcription, String keywords);

    void updatePhotosPaths(UUID ticketId, String photosPathsJson);

    TicketResponse anonymizeTicket(UUID ticketId, UUID adminId, String adminName);

    TicketStats getTicketStats(UUID agenceId);
}
