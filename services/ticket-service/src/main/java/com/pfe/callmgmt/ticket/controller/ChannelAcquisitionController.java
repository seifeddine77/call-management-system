package com.pfe.callmgmt.ticket.controller;

import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.service.QueueService;
import com.pfe.callmgmt.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints d'acquisition multicanale.
 * Simulent la réception de demandes depuis 3 sources :
 * - VoIP (Asterisk/Twilio webhook)
 * - Email (parsing IMAP)
 * - Formulaire Web (endpoint public)
 *
 * Chaque endpoint crée un ticket avec le canal approprié
 * et le met automatiquement en file d'attente (statut NOUVEAU, pas
 * d'opérateur).
 *
 * Ces endpoints sont publics (pas d'authentification JWT) car ils sont
 * appelés par des systèmes externes (Asterisk, serveur mail, site web).
 */
@Slf4j
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
@Tag(name = "Canaux d'acquisition", description = "Réception multicanale (VoIP, Email, Web)")
public class ChannelAcquisitionController {

    private final TicketService ticketService;
    private final QueueService queueService;

    // ════════════════════════════════════════════════════════════════
    // CANAL 1 — APPEL TÉLÉPHONIQUE (VoIP)
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/call")
    @Operation(summary = "Réception d'un appel VoIP (webhook Asterisk/Twilio)")
    public ResponseEntity<TicketResponse> receiveCall(@Valid @RequestBody CallChannelRequest request) {
        log.info("📞 Appel VoIP reçu de: {} ({})", request.getCallerNumber(), request.getCallerName());

        CreateTicketRequest ticketReq = new CreateTicketRequest();
        ticketReq.setClientNom(
                request.getCallerName() != null ? request.getCallerName() : "Appelant " + request.getCallerNumber());
        ticketReq.setClientTel(request.getCallerNumber());
        ticketReq.setCanal("CALL");
        ticketReq.setAgenceId(request.getAgenceId());
        ticketReq.setDescription(request.getDescription() != null
                ? request.getDescription()
                : "Appel entrant depuis " + request.getCallerNumber());
        ticketReq.setPriorite(request.getPriorite());
        // VoIP: consentement implicite via message IVR ("cet appel est susceptible d'être enregistré")
        ticketReq.setConsentementEnregistrement(true);

        // Créer le ticket sans opérateur (file d'attente)
        TicketResponse response = ticketService.createTicket(ticketReq, null, "SYSTEM-VOIP");

        log.info("📞 Ticket {} créé via VoIP, position file: {}",
                response.getNumero(), queueService.getQueueSize(request.getAgenceId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // CANAL 2 — EMAIL (IMAP parsing)
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/email")
    @Operation(summary = "Réception d'un email parsé (intégration IMAP)")
    public ResponseEntity<TicketResponse> receiveEmail(@Valid @RequestBody EmailChannelRequest request) {
        log.info("📧 Email reçu de: {} — Sujet: {}", request.getFrom(), request.getSubject());

        CreateTicketRequest ticketReq = new CreateTicketRequest();
        ticketReq.setClientNom(request.getSenderName() != null ? request.getSenderName() : request.getFrom());
        ticketReq.setClientEmail(request.getFrom());
        ticketReq.setCanal("EMAIL");
        ticketReq.setAgenceId(request.getAgenceId());
        ticketReq.setDescription("[" + request.getSubject() + "]\n" + request.getBody());
        ticketReq.setPriorite(request.getPriorite());
        // Email: consentement implicite (le client a initié le contact)
        ticketReq.setConsentementEnregistrement(true);

        TicketResponse response = ticketService.createTicket(ticketReq, null, "SYSTEM-EMAIL");

        log.info("📧 Ticket {} créé via Email, position file: {}",
                response.getNumero(), queueService.getQueueSize(request.getAgenceId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ════════════════════════════════════════════════════════════════
    // CANAL 3 — FORMULAIRE WEB (public)
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/web")
    @Operation(summary = "Réception d'un formulaire web (endpoint public)")
    public ResponseEntity<TicketResponse> receiveWebForm(@Valid @RequestBody WebFormRequest request) {
        log.info("🌐 Formulaire web reçu de: {} ({})", request.getNom(), request.getEmail());

        CreateTicketRequest ticketReq = new CreateTicketRequest();
        ticketReq.setClientNom(request.getNom());
        ticketReq.setClientTel(request.getTelephone());
        ticketReq.setClientEmail(request.getEmail());
        ticketReq.setCanal("WEB");
        ticketReq.setAgenceId(request.getAgenceId());
        ticketReq.setDescription(request.getDescription());
        ticketReq.setPriorite(request.getPriorite());
        // Web form: consentement explicite via checkbox du formulaire
        ticketReq.setConsentementEnregistrement(
                request.getConsentementTraitement() != null ? request.getConsentementTraitement() : false);

        TicketResponse response = ticketService.createTicket(ticketReq, null, "SYSTEM-WEB");

        log.info("🌐 Ticket {} créé via formulaire Web, position file: {}",
                response.getNumero(), queueService.getQueueSize(request.getAgenceId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
