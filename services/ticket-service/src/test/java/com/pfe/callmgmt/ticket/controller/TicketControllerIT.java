package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.dto.AssignTicketRequest;
import com.pfe.callmgmt.ticket.dto.CreateTicketRequest;
import com.pfe.callmgmt.ticket.dto.UpdateStatutRequest;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.repository.TicketActionRepository;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketActionRepository actionRepository;

    private static final UUID AGENCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void cleanup() {
        actionRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    private CreateTicketRequest validCreateRequest() {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setClientNom("Client Test");
        req.setClientTel("98111222");
        req.setClientEmail("client@test.com");
        req.setCanal("CALL");
        req.setAgenceId(AGENCE_ID);
        req.setDescription("Problème moteur");
        req.setPriorite((short) 3);
        return req;
    }

    private Ticket createAndSaveTicket(Ticket.Statut statut) {
        Ticket ticket = Ticket.builder()
                .clientNom("Client Test")
                .clientTel("98111222")
                .clientEmail("client@test.com")
                .canal(Ticket.Canal.CALL)
                .statut(statut)
                .priorite((short) 3)
                .agenceId(AGENCE_ID)
                .build();
        return ticketRepository.save(ticket);
    }

    // ========================================
    // POST /api/tickets — Création
    // ========================================
    @Nested
    @DisplayName("POST /api/tickets")
    class CreateTicketIT {

        @Test
        @DisplayName("201 — Création réussie d'un ticket CALL")
        void createTicket_returns201() throws Exception {
            mockMvc.perform(post("/api/tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", OPERATOR_ID)
                    .header("X-User-Email", "operator@test.com")
                    .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.numero", startsWith("T-")))
                    .andExpect(jsonPath("$.clientNom").value("Client Test"))
                    .andExpect(jsonPath("$.canal").value("CALL"))
                    .andExpect(jsonPath("$.statut").value("NOUVEAU"))
                    .andExpect(jsonPath("$.agenceId").value(AGENCE_ID.toString()));

            assertThat(ticketRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("400 — Validation échoue si clientNom manquant")
        void createTicket_missingClientNom_returns400() throws Exception {
            CreateTicketRequest request = new CreateTicketRequest();
            request.setClientTel("98111222");
            request.setCanal("CALL");
            request.setAgenceId(AGENCE_ID);
            // clientNom intentionally null to trigger validation error

            mockMvc.perform(post("/api/tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", OPERATOR_ID)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================
    // GET /api/tickets — Liste
    // ========================================
    @Nested
    @DisplayName("GET /api/tickets")
    class GetTicketsIT {

        @Test
        @DisplayName("200 — Liste paginée de tickets")
        void getTickets_returns200() throws Exception {
            createAndSaveTicket(Ticket.Statut.NOUVEAU);
            createAndSaveTicket(Ticket.Statut.EN_COURS);

            mockMvc.perform(get("/api/tickets")
                    .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("200 — Filtre par statut NOUVEAU")
        void getTickets_filterByStatut() throws Exception {
            createAndSaveTicket(Ticket.Statut.NOUVEAU);
            createAndSaveTicket(Ticket.Statut.EN_COURS);

            mockMvc.perform(get("/api/tickets")
                    .param("statut", "NOUVEAU")
                    .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].statut").value("NOUVEAU"));
        }
    }

    // ========================================
    // GET /api/tickets/{id} — Détail
    // ========================================
    @Nested
    @DisplayName("GET /api/tickets/{id}")
    class GetTicketByIdIT {

        @Test
        @DisplayName("200 — Détail d'un ticket avec actions")
        void getTicketById_returns200() throws Exception {
            Ticket ticket = createAndSaveTicket(Ticket.Statut.NOUVEAU);

            mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                    .header("X-User-Role", "ADMIN")
                    .header("X-User-Id", OPERATOR_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ticket.getId().toString()))
                    .andExpect(jsonPath("$.clientNom").value("Client Test"));
        }

        @Test
        @DisplayName("404 — Ticket non trouvé")
        void getTicketById_notFound() throws Exception {
            mockMvc.perform(get("/api/tickets/{id}", UUID.randomUUID())
                    .header("X-User-Role", "ADMIN")
                    .header("X-User-Id", OPERATOR_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // PATCH /api/tickets/{id}/statut — Transition
    // ========================================
    @Nested
    @DisplayName("PATCH /api/tickets/{id}/statut")
    class UpdateStatutIT {

        @Test
        @DisplayName("200 — Transition valide NOUVEAU → EN_COURS")
        void updateStatut_validTransition() throws Exception {
            Ticket ticket = createAndSaveTicket(Ticket.Statut.NOUVEAU);

            UpdateStatutRequest request = new UpdateStatutRequest("EN_COURS", "Prise en charge");
            mockMvc.perform(patch("/api/tickets/{id}/statut", ticket.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", OPERATOR_ID)
                    .header("X-User-Email", "operator@test.com")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("EN_COURS"));

            // Verify action logged
            assertThat(actionRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId())).hasSize(1);
        }

        @Test
        @DisplayName("400 — Transition interdite FERME → NOUVEAU")
        void updateStatut_invalidTransition() throws Exception {
            Ticket ticket = createAndSaveTicket(Ticket.Statut.FERME);

            UpdateStatutRequest request = new UpdateStatutRequest("NOUVEAU", null);
            mockMvc.perform(patch("/api/tickets/{id}/statut", ticket.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", OPERATOR_ID)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Transition interdite")));
        }
    }

    // ========================================
    // PATCH /api/tickets/{id}/assign
    // ========================================
    @Nested
    @DisplayName("PATCH /api/tickets/{id}/assign")
    class AssignTicketIT {

        @Test
        @DisplayName("200 — SUPERVISOR assigne un ticket avec auto-transition EN_COURS")
        void assignTicket_supervisorAssigns() throws Exception {
            Ticket ticket = createAndSaveTicket(Ticket.Statut.NOUVEAU);

            AssignTicketRequest request = new AssignTicketRequest(OPERATOR_ID);
            mockMvc.perform(patch("/api/tickets/{id}/assign", ticket.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", OPERATOR_ID)
                    .header("X-User-Role", "SUPERVISOR")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operateurId").value(OPERATOR_ID.toString()))
                    .andExpect(jsonPath("$.statut").value("EN_COURS")); // Auto-transition
        }
    }

    // ========================================
    // GET /api/tickets/stats
    // ========================================
    @Nested
    @DisplayName("GET /api/tickets/stats")
    class GetStatsIT {

        @Test
        @DisplayName("200 — Statistiques par statut pour une agence")
        void getStats_returns200() throws Exception {
            createAndSaveTicket(Ticket.Statut.NOUVEAU);
            createAndSaveTicket(Ticket.Statut.NOUVEAU);
            createAndSaveTicket(Ticket.Statut.EN_COURS);

            mockMvc.perform(get("/api/tickets/stats")
                    .header("X-User-Role", "ADMIN")
                    .param("agenceId", AGENCE_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nouveau").value(2))
                    .andExpect(jsonPath("$.enCours").value(1))
                    .andExpect(jsonPath("$.total").value(3));
        }
    }

    // ========================================
    // GET /api/tickets/health
    // ========================================
    @Nested
    @DisplayName("GET /api/tickets/health")
    class HealthIT {

        @Test
        @DisplayName("200 — Health check retourne OK")
        void health_returns200() throws Exception {
            mockMvc.perform(get("/api/tickets/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));
        }
    }
}
