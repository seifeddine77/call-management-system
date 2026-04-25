package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.QueueResponse;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service de file d'attente — gestion des tickets NOUVEAU non assignés.
 *
 * La file est ordonnée par :
 * 1. Priorité croissante (1 = urgente → 5 = basse)
 * 2. Date de création croissante (FIFO à priorité égale)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueueService {

    private final TicketRepository ticketRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Obtenir la file d'attente globale ou par agence.
     */
    public QueueResponse getQueue(UUID agenceId) {
        List<Ticket> queue;
        if (agenceId != null) {
            queue = ticketRepository.findByStatutAndOperateurIdIsNullAndAgenceIdOrderByPrioriteAscCreatedAtAsc(
                    Ticket.Statut.NOUVEAU, agenceId);
        } else {
            queue = ticketRepository.findByStatutAndOperateurIdIsNullOrderByPrioriteAscCreatedAtAsc(
                    Ticket.Statut.NOUVEAU);
        }

        AtomicInteger pos = new AtomicInteger(1);
        List<QueueResponse.QueueEntry> entries = queue.stream()
                .map(t -> QueueResponse.QueueEntry.builder()
                        .position(pos.getAndIncrement())
                        .ticketNumero(t.getNumero())
                        .ticketId(t.getId().toString())
                        .clientNom(t.getClientNom())
                        .canal(t.getCanal().name())
                        .priorite(t.getPriorite())
                        .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().format(DT_FMT) : null)
                        .build())
                .toList();

        return QueueResponse.builder()
                .totalEnAttente(entries.size())
                .tickets(entries)
                .build();
    }

    /**
     * Nombre de tickets en file d'attente.
     */
    public long getQueueSize(UUID agenceId) {
        if (agenceId != null) {
            return ticketRepository.countByStatutAndOperateurIdIsNullAndAgenceId(
                    Ticket.Statut.NOUVEAU, agenceId);
        }
        return ticketRepository.countByStatutAndOperateurIdIsNull(Ticket.Statut.NOUVEAU);
    }
}
