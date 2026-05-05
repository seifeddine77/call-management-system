package com.pfe.callmgmt.ticket.mapper;

import com.pfe.callmgmt.ticket.client.OrganisationServiceClient;
import com.pfe.callmgmt.ticket.dto.TicketActionResponse;
import com.pfe.callmgmt.ticket.dto.TicketResponse;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.entity.TicketAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
public class TicketMapper {

    @Autowired(required = false)
    private OrganisationServiceClient organisationClient;

    // Cache local pour éviter les appels Feign répétés
    private final Map<UUID, String> agenceNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> userNameCache = new ConcurrentHashMap<>();

    public TicketResponse toResponse(Ticket ticket) {
        return toResponse(ticket, null);
    }

    public TicketResponse toResponse(Ticket ticket, List<TicketAction> actions) {
        if (ticket == null)
            return null;

        TicketResponse.TicketResponseBuilder builder = TicketResponse.builder()
                .id(ticket.getId())
                .numero(ticket.getNumero())
                .clientNom(ticket.getClientNom())
                .clientTel(ticket.getClientTel())
                .clientEmail(ticket.getClientEmail())
                .canal(ticket.getCanal() != null ? ticket.getCanal().name() : null)
                .statut(ticket.getStatut() != null ? ticket.getStatut().name() : null)
                .priorite(ticket.getPriorite())
                .operateurId(ticket.getOperateurId())
                .agenceId(ticket.getAgenceId())
                .description(ticket.getDescription())
                .audioPath(ticket.getAudioPath())
                .transcription(ticket.getTranscription())
                .transcriptionKeywords(ticket.getTranscriptionKeywords())
                .photosPaths(ticket.getPhotosPaths())
                .consentementEnregistrement(ticket.getConsentementEnregistrement())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt());

        // Résoudre les noms via Feign (si le client est disponible)
        if (organisationClient != null) {
            if (ticket.getAgenceId() != null) {
                builder.agenceName(resolveAgenceName(ticket.getAgenceId()));
            }
            if (ticket.getOperateurId() != null) {
                builder.operateurName(resolveUserName(ticket.getOperateurId()));
            }
        }

        if (actions != null) {
            builder.actions(actions.stream()
                    .map(this::toActionResponse)
                    .toList());
        } else {
            builder.actions(Collections.emptyList());
        }

        return builder.build();
    }

    public TicketActionResponse toActionResponse(TicketAction action) {
        if (action == null)
            return null;

        return TicketActionResponse.builder()
                .id(action.getId())
                .userId(action.getUserId())
                .userName(action.getUserName())
                .action(action.getAction())
                .oldValue(action.getOldValue())
                .newValue(action.getNewValue())
                .notes(action.getNotes())
                .createdAt(action.getCreatedAt())
                .build();
    }

    // ── Résolution noms avec cache et fallback silencieux ──

    private String resolveAgenceName(UUID agenceId) {
        return agenceNameCache.computeIfAbsent(agenceId, id -> {
            try {
                Map<String, Object> agence = organisationClient.getAgenceById(id);
                return (String) agence.get("nom");
            } catch (Exception e) {
                log.debug("Impossible de résoudre le nom de l'agence {}", id);
                return null;
            }
        });
    }

    private String resolveUserName(UUID userId) {
        return userNameCache.computeIfAbsent(userId, id -> {
            try {
                Map<String, Object> user = organisationClient.getUserById(id);
                String prenom = (String) user.getOrDefault("prenom", "");
                String nom = (String) user.getOrDefault("nom", "");
                return (prenom + " " + nom).trim();
            } catch (Exception e) {
                log.debug("Impossible de résoudre le nom de l'opérateur {}", id);
                return null;
            }
        });
    }
}
