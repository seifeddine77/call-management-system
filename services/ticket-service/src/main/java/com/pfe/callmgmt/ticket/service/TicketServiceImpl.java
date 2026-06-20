package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.entity.TicketAction;
import com.pfe.callmgmt.ticket.event.TicketCreatedEvent;
import com.pfe.callmgmt.ticket.event.TicketEventPublisher;
import com.pfe.callmgmt.ticket.event.TicketResolvedEvent;
import com.pfe.callmgmt.ticket.event.TicketStatusChangedEvent;
import com.pfe.callmgmt.ticket.exception.AccessDeniedException;
import com.pfe.callmgmt.ticket.exception.InvalidTransitionException;
import com.pfe.callmgmt.ticket.exception.NotFoundException;
import com.pfe.callmgmt.ticket.mapper.TicketMapper;
import com.pfe.callmgmt.ticket.repository.TicketActionRepository;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String SYSTEM_USER_UUID = "00000000-0000-0000-0000-000000000000";

    private final TicketRepository ticketRepository;
    private final TicketActionRepository actionRepository;
    private final TicketMapper ticketMapper;

    @Nullable
    private final TicketEventPublisher eventPublisher;

    @Nullable
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public TicketServiceImpl(
            TicketRepository ticketRepository,
            TicketActionRepository actionRepository,
            TicketMapper ticketMapper,
            @Autowired(required = false) @Nullable TicketEventPublisher eventPublisher,
            @Autowired(required = false) @Nullable SimpMessagingTemplate messagingTemplate) {
        this.ticketRepository = ticketRepository;
        this.actionRepository = actionRepository;
        this.ticketMapper = ticketMapper;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public TicketResponse createTicket(CreateTicketRequest request, UUID operateurId, String operateurName) {
        // CDC: Only CALL tickets are auto-assigned to the creating operator.
        // EMAIL/WEB tickets go to the queue (operateurId=null) for any operator to pick up.
        Ticket.Canal canal = Ticket.Canal.valueOf(request.getCanal().toUpperCase());
        boolean autoAssign = (canal == Ticket.Canal.CALL) && (operateurId != null);

        Ticket ticket = Ticket.builder()
                .clientNom(request.getClientNom())
                .clientTel(request.getClientTel())
                .clientEmail(request.getClientEmail())
                .canal(canal)
                .statut(Ticket.Statut.NOUVEAU)
                .priorite(request.getPriorite() != null ? request.getPriorite() : (short) 3)
                .operateurId(autoAssign ? operateurId : null)
                .agenceId(request.getAgenceId())
                .description(request.getDescription())
                .consentementEnregistrement(
                        request.getConsentementEnregistrement() != null
                                && request.getConsentementEnregistrement())
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("Ticket created: {} ({})", ticket.getNumero(), ticket.getCanal());

        // Log action (use SYSTEM UUID for channel-originated tickets without operator)
        UUID actionUserId = operateurId != null ? operateurId
                : UUID.fromString(SYSTEM_USER_UUID);
        String actionUserName = operateurName != null ? operateurName : SYSTEM_USER_NAME;
        logAction(ticket, actionUserId, actionUserName, "CREE", null, ticket.getStatut().name(), null);

        // Publish RabbitMQ event
        if (eventPublisher != null) {
            eventPublisher.publishTicketCreated(TicketCreatedEvent.builder()
                    .ticketId(ticket.getId())
                    .numero(ticket.getNumero())
                    .clientNom(ticket.getClientNom())
                    .clientEmail(ticket.getClientEmail())
                    .canal(ticket.getCanal().name())
                    .agenceId(ticket.getAgenceId())
                    .operateurId(ticket.getOperateurId())
                    .build());
        }

        // Send WebSocket message
        TicketResponse response = ticketMapper.toResponse(ticket);
        sendWebSocket("/topic/tickets.new", response);

        return response;
    }

    @Override
    public TicketResponse assignTicket(UUID ticketId, UUID operateurId, UUID requesterId, String requesterRole,
            UUID requesterAgenceId) {
        Ticket ticket = findTicketOrThrow(ticketId);

        // RBAC: OPERATOR can only take unassigned tickets (self-assign)
        if (ROLE_OPERATOR.equals(requesterRole)) {
            if (ticket.getOperateurId() != null) {
                throw new AccessDeniedException("Un opérateur ne peut prendre qu'un ticket non assigné");
            }
            if (!operateurId.equals(requesterId)) {
                throw new AccessDeniedException("Un opérateur ne peut s'assigner qu'à lui-même");
            }
            // CDC 1.C — Vérifier que le ticket appartient à l'agence de l'opérateur
            if (requesterAgenceId != null && !requesterAgenceId.equals(ticket.getAgenceId())) {
                throw new AccessDeniedException(
                        "Impossible de prendre un ticket d'une autre agence");
            }
        }
        // SUPERVISOR and ADMIN can assign to any operator

        UUID oldOperateur = ticket.getOperateurId();
        ticket.setOperateurId(operateurId);

        // Auto-transition NOUVEAU → EN_COURS on assignment
        boolean statusChanged = false;
        String oldStatutName = ticket.getStatut().name();
        if (ticket.getStatut() == Ticket.Statut.NOUVEAU) {
            ticket.setStatut(Ticket.Statut.EN_COURS);
            statusChanged = true;
        }

        ticket = ticketRepository.save(ticket);
        log.info("Ticket {} assigned to operator: {}", ticket.getNumero(), operateurId);

        logAction(ticket, requesterId, null, "ASSIGNE",
                oldOperateur != null ? oldOperateur.toString() : "non assigné",
                operateurId.toString(), null);

        // Publish status-changed event for NOUVEAU → EN_COURS auto-transition
        if (statusChanged && eventPublisher != null) {
            eventPublisher.publishTicketStatusChanged(TicketStatusChangedEvent.builder()
                    .ticketId(ticket.getId())
                    .numero(ticket.getNumero())
                    .oldStatut(oldStatutName)
                    .newStatut(ticket.getStatut().name())
                    .agenceId(ticket.getAgenceId())
                    .build());
        }

        TicketResponse response = ticketMapper.toResponse(ticket);
        sendWebSocket("/topic/tickets.update", response);

        return response;
    }

    @Override
    public TicketResponse updateStatut(UUID ticketId, UpdateStatutRequest request, UUID userId, String userName) {
        Ticket ticket = findTicketOrThrow(ticketId);

        Ticket.Statut newStatut;
        try {
            newStatut = Ticket.Statut.valueOf(request.getNewStatut().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransitionException("Statut invalide: " + request.getNewStatut());
        }

        // Validate transition
        if (!ticket.getStatut().canTransitionTo(newStatut)) {
            throw new InvalidTransitionException(
                    String.format("Transition interdite: %s → %s", ticket.getStatut(), newStatut));
        }

        String oldStatut = ticket.getStatut().name();
        ticket.setStatut(newStatut);
        ticket = ticketRepository.save(ticket);
        log.info("Ticket {} status changed: {} → {}", ticket.getNumero(), oldStatut, newStatut);

        logAction(ticket, userId, userName, "STATUT_CHANGE", oldStatut, newStatut.name(), request.getNotes());

        // Publish status-changed event for ALL transitions
        if (eventPublisher != null) {
            eventPublisher.publishTicketStatusChanged(TicketStatusChangedEvent.builder()
                    .ticketId(ticket.getId())
                    .numero(ticket.getNumero())
                    .oldStatut(oldStatut)
                    .newStatut(newStatut.name())
                    .agenceId(ticket.getAgenceId())
                    .build());

            // Additionally publish TicketResolvedEvent for resolution time tracking
            if (newStatut == Ticket.Statut.RESOLU) {
                eventPublisher.publishTicketResolved(TicketResolvedEvent.builder()
                        .ticketId(ticket.getId())
                        .numero(ticket.getNumero())
                        .clientNom(ticket.getClientNom())
                        .clientEmail(ticket.getClientEmail())
                        .agenceId(ticket.getAgenceId())
                        .ticketCreatedAt(ticket.getCreatedAt())
                        .build());
            }
        }

        TicketResponse response = ticketMapper.toResponse(ticket);
        sendWebSocket("/topic/tickets.update", response);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getTickets(TicketFilter filter, String userRole, UUID userId, UUID userAgenceId) {
        // CDC 1.C — OPERATOR ne voit que les tickets de son agence
        // Security: always force operator's agence regardless of filter value
        UUID effectiveAgenceId = filter.getAgenceId();
        if (ROLE_OPERATOR.equals(userRole)) {
            effectiveAgenceId = userAgenceId;
        }

        // Native query accepts String params directly
        String statut = (filter.getStatut() != null && !filter.getStatut().isBlank())
                ? filter.getStatut().toUpperCase()
                : null;
        String canal = (filter.getCanal() != null && !filter.getCanal().isBlank())
                ? filter.getCanal().toUpperCase()
                : null;
        Short priorite = filter.getPriorite();

        PageRequest pageable = PageRequest.of(filter.getPage(), filter.getSize());

        return ticketRepository.findByFilters(
                statut, canal, priorite, effectiveAgenceId,
                filter.getOperateurId(),
                filter.getDateFrom(), filter.getDateTo(),
                filter.getSearch(), pageable).map(ticketMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID id, String userRole, UUID userId, UUID userAgenceId) {
        Ticket ticket = findTicketOrThrow(id);

        // CDC 1.C — OPERATOR ne peut consulter que les tickets de son agence
        if (ROLE_OPERATOR.equals(userRole) && userAgenceId != null
                && !userAgenceId.equals(ticket.getAgenceId())) {
            throw new AccessDeniedException(
                    "Accès refusé — ce ticket appartient à une autre agence");
        }

        List<TicketAction> actions = actionRepository.findByTicketIdOrderByCreatedAtDesc(id);
        return ticketMapper.toResponse(ticket, actions);
    }

    @Override
    public void updateAudioTranscription(UUID ticketId, String audioPath, String transcription, String keywords) {
        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAudioPath(audioPath);
        ticket.setTranscription(transcription);
        ticket.setTranscriptionKeywords(keywords);
        ticketRepository.save(ticket);
        log.info("Audio transcription updated for ticket: {}", ticket.getNumero());

        logAction(ticket, UUID.fromString(SYSTEM_USER_UUID),
                SYSTEM_USER_NAME, "TRANSCRIPTION_AJOUTEE", null, null, "Transcription audio ajoutée");
    }

    @Override
    public void updatePhotosPaths(UUID ticketId, String photosPathsJson) {
        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setPhotosPaths(photosPathsJson);
        ticketRepository.save(ticket);
        log.info("📸 Photos mises à jour pour ticket: {}", ticket.getNumero());

        logAction(ticket, UUID.fromString(SYSTEM_USER_UUID),
                SYSTEM_USER_NAME, "PHOTOS_AJOUTEES", null, null, "Photos sinistre ajoutées");
    }

    @Override
    public TicketResponse anonymizeTicket(UUID ticketId, UUID adminId, String adminName) {
        Ticket ticket = findTicketOrThrow(ticketId);

        // Anonymiser les données personnelles (RGPD — droit à l'oubli)
        String oldNom = ticket.getClientNom();
        ticket.setClientNom("***ANONYMISÉ***");
        ticket.setClientTel("***");
        ticket.setClientEmail("***@anonymise.rgpd");
        ticket.setDescription("[Contenu anonymisé conformément au RGPD]");

        // Supprimer l'audio, la transcription et les photos
        ticket.setAudioPath(null);
        ticket.setTranscription(null);
        ticket.setTranscriptionKeywords(null);
        ticket.setPhotosPaths(null);

        ticket = ticketRepository.save(ticket);
        log.info("🔒 RGPD: Ticket {} anonymisé par admin {}", ticket.getNumero(), adminId);

        logAction(ticket, adminId, adminName, "ANONYMISE",
                "Client: " + oldNom, "***ANONYMISÉ***", "Anonymisation RGPD — droit à l'oubli");

        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketStats getTicketStats(UUID agenceId) {
        long nouveau;
        long enCours;
        long resolu;
        long ferme;

        if (agenceId != null) {
            nouveau = ticketRepository.countByStatutAndAgenceId(Ticket.Statut.NOUVEAU, agenceId);
            enCours = ticketRepository.countByStatutAndAgenceId(Ticket.Statut.EN_COURS, agenceId);
            resolu = ticketRepository.countByStatutAndAgenceId(Ticket.Statut.RESOLU, agenceId);
            ferme = ticketRepository.countByStatutAndAgenceId(Ticket.Statut.FERME, agenceId);
        } else {
            nouveau = ticketRepository.countByStatut(Ticket.Statut.NOUVEAU);
            enCours = ticketRepository.countByStatut(Ticket.Statut.EN_COURS);
            resolu = ticketRepository.countByStatut(Ticket.Statut.RESOLU);
            ferme = ticketRepository.countByStatut(Ticket.Statut.FERME);
        }

        return TicketStats.builder()
                .nouveau(nouveau)
                .enCours(enCours)
                .resolu(resolu)
                .ferme(ferme)
                .total(nouveau + enCours + resolu + ferme)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketStats getTicketStatsCreatedBetween(LocalDate from, LocalDate to) {
        // Plage [from 00:00, to+1j 00:00[ → borne 'to' incluse sur toute la journée.
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        long nouveau = ticketRepository.countByStatutAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Ticket.Statut.NOUVEAU, start, end);
        long enCours = ticketRepository.countByStatutAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Ticket.Statut.EN_COURS, start, end);
        long resolu = ticketRepository.countByStatutAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Ticket.Statut.RESOLU, start, end);
        long ferme = ticketRepository.countByStatutAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Ticket.Statut.FERME, start, end);
        return TicketStats.builder()
                .nouveau(nouveau)
                .enCours(enCours)
                .resolu(resolu)
                .ferme(ferme)
                .total(nouveau + enCours + resolu + ferme)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyTicketCount> getDailyTicketCounts(LocalDate from, LocalDate to) {
        // Borne haute exclusive : on inclut tout le jour `to`.
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        List<Object[]> rows = ticketRepository.countCreatedPerDayByCanal(fromDt, toDt);

        // Agrégation des lignes [jour, canal, count] en un DTO par jour (trié par date).
        Map<LocalDate, DailyTicketCount> byDay = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            String canal = row[1] != null ? row[1].toString() : "";
            long cnt = ((Number) row[2]).longValue();

            DailyTicketCount dc = byDay.computeIfAbsent(day, d -> DailyTicketCount.builder()
                    .date(d).totalTickets(0).ticketsCall(0).ticketsEmail(0).ticketsWeb(0).build());
            dc.setTotalTickets(dc.getTotalTickets() + cnt);
            switch (canal.toUpperCase()) {
                case "CALL" -> dc.setTicketsCall(dc.getTicketsCall() + cnt);
                case "EMAIL" -> dc.setTicketsEmail(dc.getTicketsEmail() + cnt);
                case "WEB" -> dc.setTicketsWeb(dc.getTicketsWeb() + cnt);
                default -> log.debug("Canal inconnu compté dans le total uniquement: {}", canal);
            }
        }
        return new ArrayList<>(byDay.values());
    }

    // ========== Helpers ==========


    private Ticket findTicketOrThrow(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket non trouvé: " + id));
    }

    private void logAction(Ticket ticket, UUID userId, String userName,
            String action, String oldValue, String newValue, String notes) {
        TicketAction ticketAction = TicketAction.builder()
                .ticket(ticket)
                .userId(userId)
                .userName(userName)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .notes(notes)
                .build();
        actionRepository.save(ticketAction);
    }

    private void sendWebSocket(String destination, Object payload) {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend(destination, payload);
            } catch (Exception e) {
                log.warn("Failed to send WebSocket message to {}: {}", destination, e.getMessage(), e);
            }
        }
    }
}
