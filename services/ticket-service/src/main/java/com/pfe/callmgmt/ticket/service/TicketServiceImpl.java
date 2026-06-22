package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.entity.TicketAction;
import com.pfe.callmgmt.ticket.event.TicketAnonymizedEvent;
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

/**
 * Cœur métier du cycle de vie d'un ticket de réclamation (création → résolution).
 *
 * <p>Responsabilités : création multicanale (CALL/EMAIL/WEB), transitions de statut contrôlées,
 * journalisation des actions (audit), publication d'événements RabbitMQ et <b>détection automatique
 * de priorité unifiée</b> sur les 3 canaux.</p>
 *
 * <p>Choix d'architecture : aucune clé étrangère vers les autres services — la cohérence inter-services
 * passe par des événements (ticket.created / resolved / status-changed) pour préserver le découplage.</p>
 */
@Slf4j
@Service
@Transactional
public class TicketServiceImpl implements TicketService {

    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String SYSTEM_USER_NAME = "SYSTEM";
    private static final String SYSTEM_USER_UUID = "00000000-0000-0000-0000-000000000000";

    // Niveaux de priorité (évite les nombres magiques — cf. détection automatique).
    private static final short PRIORITE_URGENTE = 1; // P1
    private static final short PRIORITE_HAUTE = 2;   // P2
    private static final short PRIORITE_NORMALE = 3; // P3

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

    /**
     * Détection automatique et unifiée de la priorité à partir du texte de la réclamation
     * (description EMAIL/WEB, ou sujet+corps pour les emails IMAP, ou transcription CALL).
     * Appliquée de façon identique sur les 3 canaux pour garantir la cohérence du tri,
     * et surchargée par toute priorité explicitement fournie (saisie opérateur / formulaire).
     *
     *  - P1 (Urgente) : urgence explicite ou sinistre grave / sécurité (accident, incendie, vol,
     *    véhicule immobilisé, blessé).
     *  - P2 (Haute)   : besoin de traitement rapide ou panne mécanique (panne, remorquage, fuite).
     *  - P3 (Normale) : par défaut.
     */
    static short detectPriority(String text) {
        if (text == null || text.isBlank()) {
            return PRIORITE_NORMALE;
        }
        String t = text.toLowerCase();
        if (t.contains("urgent") || t.contains("critique") || t.contains("accident")
                || t.contains("incendie") || t.contains("danger") || t.contains("immobilis")
                || t.contains("bless") || t.contains("vol ") || t.contains("volé")) {
            return PRIORITE_URGENTE;
        }
        if (t.contains("panne") || t.contains("important") || t.contains("rapidement")
                || t.contains("remorqu") || t.contains("fuite") || t.contains("bloqu")) {
            return PRIORITE_HAUTE;
        }
        return PRIORITE_NORMALE;
    }

    /**
     * Crée un ticket de réclamation, quel que soit le canal d'origine.
     * Seuls les tickets CALL sont auto-assignés à l'opérateur créateur ; EMAIL/WEB partent en file
     * d'attente. La priorité est détectée automatiquement depuis la description si non fournie, puis
     * un événement {@code ticket.created} est publié pour les services aval.
     *
     * @param request       données du ticket (client, canal, description, priorité optionnelle…)
     * @param operateurId   identifiant de l'opérateur créateur (null pour une création système/canal)
     * @param operateurName nom affiché de l'opérateur (ou identifiant système, pour l'audit)
     * @return le ticket créé, avec numéro généré et statut NOUVEAU
     */
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
                // Priorité unifiée multi-canal : la valeur explicite (saisie opérateur / formulaire)
                // prime ; sinon détection automatique par mots-clés sur la description (EMAIL/WEB/CALL).
                .priorite(request.getPriorite() != null ? request.getPriorite() : detectPriority(request.getDescription()))
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

        // Pour un CALL, le contenu réel (donc l'urgence) n'arrive qu'avec la transcription Whisper,
        // postérieure à la création. On ré-évalue donc la priorité sur la transcription et on ne
        // garde que la plus urgente (numéro le plus bas) : une urgence détectée fait monter un
        // ticket P3 → P1, sans jamais rétrograder une priorité fixée explicitement.
        if (transcription != null && !transcription.isBlank()) {
            short detected = detectPriority(transcription);
            short current = ticket.getPriorite() != null ? ticket.getPriorite() : 3;
            if (detected < current) {
                ticket.setPriorite(detected);
                logAction(ticket, UUID.fromString(SYSTEM_USER_UUID), SYSTEM_USER_NAME,
                        "PRIORITE_AJUSTEE", String.valueOf(current), String.valueOf(detected),
                        "Priorité relevée d'après la transcription audio");
            }
        }

        ticketRepository.save(ticket);
        log.info("Audio transcription updated for ticket: {} (priorité={})", ticket.getNumero(), ticket.getPriorite());

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

        // Effacement inter-services GARANTI via événement durable (RabbitMQ) :
        // ai-service (audio + transcription) et decision-service (décision + courriers)
        // consomment l'événement, avec rejeu automatique si l'un est indisponible.
        if (eventPublisher != null) {
            eventPublisher.publishTicketAnonymized(new TicketAnonymizedEvent(ticket.getId()));
        }

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
