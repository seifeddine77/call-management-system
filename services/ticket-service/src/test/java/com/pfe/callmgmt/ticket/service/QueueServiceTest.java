package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.QueueResponse;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private QueueService queueService;

    @Test
    @DisplayName("getQueue sans agenceId retourne tous les tickets NOUVEAU non assignés")
    void getQueue_noAgence_returnsAllUnassigned() {
        Ticket t1 = Ticket.builder()
                .id(UUID.randomUUID())
                .numero("TK-001")
                .clientNom("Client A")
                .canal(Ticket.Canal.CALL)
                .priorite((short) 1)
                .createdAt(LocalDateTime.now())
                .build();
        Ticket t2 = Ticket.builder()
                .id(UUID.randomUUID())
                .numero("TK-002")
                .clientNom("Client B")
                .canal(Ticket.Canal.EMAIL)
                .priorite((short) 3)
                .createdAt(LocalDateTime.now())
                .build();

        when(ticketRepository.findByStatutAndOperateurIdIsNullOrderByPrioriteAscCreatedAtAsc(Ticket.Statut.NOUVEAU))
                .thenReturn(List.of(t1, t2));

        QueueResponse response = queueService.getQueue(null);

        assertThat(response.getTotalEnAttente()).isEqualTo(2);
        assertThat(response.getTickets()).hasSize(2);
        assertThat(response.getTickets().get(0).getPosition()).isEqualTo(1);
        assertThat(response.getTickets().get(0).getTicketNumero()).isEqualTo("TK-001");
        assertThat(response.getTickets().get(1).getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("getQueue avec agenceId filtre par agence")
    void getQueue_withAgence_filtersById() {
        UUID agenceId = UUID.randomUUID();
        when(ticketRepository.findByStatutAndOperateurIdIsNullAndAgenceIdOrderByPrioriteAscCreatedAtAsc(
                Ticket.Statut.NOUVEAU, agenceId))
                .thenReturn(List.of());

        QueueResponse response = queueService.getQueue(agenceId);

        assertThat(response.getTotalEnAttente()).isZero();
        assertThat(response.getTickets()).isEmpty();
    }

    @Test
    @DisplayName("getQueueSize sans agence retourne le total global")
    void getQueueSize_noAgence_returnsGlobalCount() {
        when(ticketRepository.countByStatutAndOperateurIdIsNull(Ticket.Statut.NOUVEAU))
                .thenReturn(15L);

        long size = queueService.getQueueSize(null);

        assertThat(size).isEqualTo(15);
    }

    @Test
    @DisplayName("getQueueSize avec agence retourne le total filtré")
    void getQueueSize_withAgence_returnsFilteredCount() {
        UUID agenceId = UUID.randomUUID();
        when(ticketRepository.countByStatutAndOperateurIdIsNullAndAgenceId(
                Ticket.Statut.NOUVEAU, agenceId))
                .thenReturn(5L);

        long size = queueService.getQueueSize(agenceId);

        assertThat(size).isEqualTo(5);
    }
}
