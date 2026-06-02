package com.pfe.callmgmt.ticket.mapper;

import com.pfe.callmgmt.ticket.dto.TicketActionResponse;
import com.pfe.callmgmt.ticket.dto.TicketResponse;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.entity.TicketAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TicketMapperTest {

    @InjectMocks
    private TicketMapper ticketMapper;

    @Test
    @DisplayName("toResponse mappe tous les champs du ticket")
    void toResponse_mapsAllFields() {
        UUID ticketId = UUID.randomUUID();
        UUID operateurId = UUID.randomUUID();
        UUID agenceId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .numero("TK-001")
                .clientNom("Jean Dupont")
                .clientTel("0612345678")
                .clientEmail("jean@test.fr")
                .canal(Ticket.Canal.CALL)
                .statut(Ticket.Statut.EN_COURS)
                .priorite((short) 2)
                .operateurId(operateurId)
                .agenceId(agenceId)
                .description("Panne moteur")
                .transcription("Le client signale une panne")
                .createdAt(now)
                .build();

        TicketResponse response = ticketMapper.toResponse(ticket);

        assertThat(response.getId()).isEqualTo(ticketId);
        assertThat(response.getNumero()).isEqualTo("TK-001");
        assertThat(response.getClientNom()).isEqualTo("Jean Dupont");
        assertThat(response.getClientTel()).isEqualTo("0612345678");
        assertThat(response.getClientEmail()).isEqualTo("jean@test.fr");
        assertThat(response.getCanal()).isEqualTo("CALL");
        assertThat(response.getStatut()).isEqualTo("EN_COURS");
        assertThat(response.getPriorite()).isEqualTo((short) 2);
        assertThat(response.getDescription()).isEqualTo("Panne moteur");
        assertThat(response.getActions()).isEmpty();
    }

    @Test
    @DisplayName("toResponse avec actions les inclut dans la réponse")
    void toResponse_withActions_includesActions() {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .numero("TK-002")
                .clientNom("Marie")
                .canal(Ticket.Canal.WEB)
                .statut(Ticket.Statut.NOUVEAU)
                .priorite((short) 3)
                .build();

        TicketAction action = TicketAction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .userName("Admin")
                .action("CREATION")
                .notes("Ticket créé via web")
                .createdAt(LocalDateTime.now())
                .build();

        TicketResponse response = ticketMapper.toResponse(ticket, List.of(action));

        assertThat(response.getActions()).hasSize(1);
        assertThat(response.getActions().get(0).getAction()).isEqualTo("CREATION");
        assertThat(response.getActions().get(0).getUserName()).isEqualTo("Admin");
    }

    @Test
    @DisplayName("toResponse retourne null pour ticket null")
    void toResponse_nullTicket_returnsNull() {
        assertThat(ticketMapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("toActionResponse mappe tous les champs de l'action")
    void toActionResponse_mapsAllFields() {
        UUID actionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        TicketAction action = TicketAction.builder()
                .id(actionId)
                .userId(userId)
                .userName("Opérateur1")
                .action("ASSIGN")
                .oldValue("NOUVEAU")
                .newValue("EN_COURS")
                .notes("Pris en charge")
                .createdAt(now)
                .build();

        TicketActionResponse response = ticketMapper.toActionResponse(action);

        assertThat(response.getId()).isEqualTo(actionId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUserName()).isEqualTo("Opérateur1");
        assertThat(response.getAction()).isEqualTo("ASSIGN");
        assertThat(response.getOldValue()).isEqualTo("NOUVEAU");
        assertThat(response.getNewValue()).isEqualTo("EN_COURS");
    }

    @Test
    @DisplayName("toActionResponse retourne null pour action null")
    void toActionResponse_nullAction_returnsNull() {
        assertThat(ticketMapper.toActionResponse(null)).isNull();
    }
}
