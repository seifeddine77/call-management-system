package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.dto.CallChannelRequest;
import com.pfe.callmgmt.ticket.dto.EmailChannelRequest;
import com.pfe.callmgmt.ticket.dto.WebFormRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration pour l'acquisition multicanale (Module 2 CDC).
 * Couvre les 3 canaux (VoIP, Email, Web) et la file d'attente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelAcquisitionControllerIT {

    // --- SonarCloud: extracted string constants ---
    private static final String API_CHANNELS_CALL = "/api/channels/call";
    private static final String API_TICKETS_QUEUE = "/api/tickets/queue";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUT_NOUVEAU = "NOUVEAU";
    private static final String JSON_STATUT = "$.statut";
    private static final String JSON_CANAL = "$.canal";
    private static final String JSON_CLIENT_NOM = "$.clientNom";
    private static final String JSON_OPERATEUR_ID = "$.operateurId";
    private static final String JSON_TOTAL_EN_ATTENTE = "$.totalEnAttente";
    private static final String JSON_TICKETS_0_CLIENT_NOM = "$.tickets[0].clientNom";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketActionRepository actionRepository;

    private static final UUID AGENCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void cleanup() {
        actionRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    // ════════════════════════════════════════════════════════
    // CANAL 1 — APPEL VoIP
    // ════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/channels/call — VoIP")
    class CallChannelIT {

        @Test
        @DisplayName("201 — Appel VoIP crée un ticket NOUVEAU sans opérateur")
        void receiveCall_returns201() throws Exception {
            CallChannelRequest req = new CallChannelRequest();
            req.setCallerNumber("+216 98 111 222");
            req.setCallerName("Mohamed Ben Ali");
            req.setAgenceId(AGENCE_ID);
            req.setDescription("Problème frein arrière");
            req.setPriorite((short) 2);

            mockMvc.perform(post(API_CHANNELS_CALL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.numero", startsWith("T-")))
                    .andExpect(jsonPath(JSON_CANAL).value("CALL"))
                    .andExpect(jsonPath(JSON_STATUT).value(STATUT_NOUVEAU))
                    .andExpect(jsonPath(JSON_CLIENT_NOM).value("Mohamed Ben Ali"))
                    .andExpect(jsonPath("$.clientTel").value("+216 98 111 222"))
                    .andExpect(jsonPath(JSON_OPERATEUR_ID).isEmpty())
                    .andExpect(jsonPath("$.priorite").value(2));

            assertThat(ticketRepository.count()).isEqualTo(1);
            Ticket saved = ticketRepository.findAll().get(0);
            assertThat(saved.getOperateurId()).isNull(); // En file d'attente
        }

        @Test
        @DisplayName("201 — Appel VoIP sans nom utilise le numéro comme nom")
        void receiveCall_noName_usesNumber() throws Exception {
            CallChannelRequest req = new CallChannelRequest();
            req.setCallerNumber("71234567");
            req.setAgenceId(AGENCE_ID);

            mockMvc.perform(post(API_CHANNELS_CALL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientNom", startsWith("Appelant")));
        }

        @Test
        @DisplayName("400 — Appel VoIP sans numéro retourne 400")
        void receiveCall_noNumber_returns400() throws Exception {
            CallChannelRequest req = new CallChannelRequest();
            req.setAgenceId(AGENCE_ID);
            // callerNumber manquant

            mockMvc.perform(post(API_CHANNELS_CALL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════════════════════════════════
    // CANAL 2 — EMAIL
    // ════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/channels/email — Email")
    class EmailChannelIT {

        @Test
        @DisplayName("201 — Email parsé crée un ticket EMAIL sans opérateur")
        void receiveEmail_returns201() throws Exception {
            EmailChannelRequest req = new EmailChannelRequest();
            req.setFrom("client@gmail.com");
            req.setSenderName("Fatma Trabelsi");
            req.setSubject("Demande pièce alternateur");
            req.setBody("Bonjour, je cherche un alternateur pour Peugeot 308 2018.");
            req.setAgenceId(AGENCE_ID);

            mockMvc.perform(post("/api/channels/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(JSON_CANAL).value("EMAIL"))
                    .andExpect(jsonPath(JSON_STATUT).value(STATUT_NOUVEAU))
                    .andExpect(jsonPath(JSON_CLIENT_NOM).value("Fatma Trabelsi"))
                    .andExpect(jsonPath("$.clientEmail").value("client@gmail.com"))
                    .andExpect(jsonPath(JSON_OPERATEUR_ID).isEmpty())
                    .andExpect(jsonPath("$.description", containsString("alternateur")));

            assertThat(ticketRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("400 — Email sans sujet retourne 400")
        void receiveEmail_noSubject_returns400() throws Exception {
            EmailChannelRequest req = new EmailChannelRequest();
            req.setFrom("client@test.com");
            req.setBody("Corps du message");
            req.setAgenceId(AGENCE_ID);
            // subject manquant

            mockMvc.perform(post("/api/channels/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════════════════════════════════
    // CANAL 3 — FORMULAIRE WEB
    // ════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/channels/web — Web Form")
    class WebChannelIT {

        @Test
        @DisplayName("201 — Formulaire web crée un ticket WEB sans opérateur")
        void receiveWebForm_returns201() throws Exception {
            WebFormRequest req = new WebFormRequest();
            req.setNom("Ahmed Bouazizi");
            req.setTelephone("55123456");
            req.setEmail("ahmed@outlook.com");
            req.setDescription("Besoin devis plaquettes de frein Renault Clio");
            req.setAgenceId(AGENCE_ID);
            req.setPriorite((short) 4);

            mockMvc.perform(post("/api/channels/web")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(JSON_CANAL).value("WEB"))
                    .andExpect(jsonPath(JSON_STATUT).value(STATUT_NOUVEAU))
                    .andExpect(jsonPath(JSON_CLIENT_NOM).value("Ahmed Bouazizi"))
                    .andExpect(jsonPath("$.clientTel").value("55123456"))
                    .andExpect(jsonPath("$.clientEmail").value("ahmed@outlook.com"))
                    .andExpect(jsonPath(JSON_OPERATEUR_ID).isEmpty())
                    .andExpect(jsonPath("$.priorite").value(4));

            assertThat(ticketRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("400 — Formulaire sans nom retourne 400")
        void receiveWebForm_noName_returns400() throws Exception {
            WebFormRequest req = new WebFormRequest();
            req.setDescription("Ma demande");
            req.setAgenceId(AGENCE_ID);
            // nom manquant

            mockMvc.perform(post("/api/channels/web")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════════════════════════════════
    // FILE D'ATTENTE (Queue)
    // ════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/tickets/queue — File d'attente")
    class QueueIT {

        @Test
        @DisplayName("200 — File d'attente vide")
        void getQueue_empty() throws Exception {
            mockMvc.perform(get(API_TICKETS_QUEUE)
                    .header(HEADER_USER_ROLE, ROLE_ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_TOTAL_EN_ATTENTE).value(0))
                    .andExpect(jsonPath("$.tickets", hasSize(0)));
        }

        @Test
        @DisplayName("200 — File d'attente avec tickets ordonnés par priorité")
        void getQueue_orderedByPriority() throws Exception {
            // Créer 3 tickets non assignés avec différentes priorités
            ticketRepository.save(Ticket.builder()
                    .clientNom("Client3").canal(Ticket.Canal.WEB).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 5).agenceId(AGENCE_ID).build());
            ticketRepository.save(Ticket.builder()
                    .clientNom("Client1").canal(Ticket.Canal.CALL).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 1).agenceId(AGENCE_ID).build());
            ticketRepository.save(Ticket.builder()
                    .clientNom("Client2").canal(Ticket.Canal.EMAIL).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 3).agenceId(AGENCE_ID).build());

            // Un ticket assigné (ne devrait PAS être dans la file)
            ticketRepository.save(Ticket.builder()
                    .clientNom("Client Assigné").canal(Ticket.Canal.CALL).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 1).agenceId(AGENCE_ID)
                    .operateurId(UUID.randomUUID()).build());

            mockMvc.perform(get(API_TICKETS_QUEUE)
                    .header(HEADER_USER_ROLE, ROLE_ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_TOTAL_EN_ATTENTE).value(3)) // 3, pas 4
                    .andExpect(jsonPath(JSON_TICKETS_0_CLIENT_NOM).value("Client1")) // P1
                    .andExpect(jsonPath("$.tickets[1].clientNom").value("Client2")) // P3
                    .andExpect(jsonPath("$.tickets[2].clientNom").value("Client3")); // P5
        }

        @Test
        @DisplayName("200 — File d'attente filtrée par agence pour opérateur")
        void getQueue_filteredByAgence() throws Exception {
            UUID otherAgence = UUID.fromString("22222222-2222-2222-2222-222222222222");

            ticketRepository.save(Ticket.builder()
                    .clientNom("Mon agence").canal(Ticket.Canal.CALL).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 3).agenceId(AGENCE_ID).build());
            ticketRepository.save(Ticket.builder()
                    .clientNom("Autre agence").canal(Ticket.Canal.WEB).statut(Ticket.Statut.NOUVEAU)
                    .priorite((short) 3).agenceId(otherAgence).build());

            // Opérateur voit uniquement sa file d'agence via X-User-Agence-Id
            mockMvc.perform(get(API_TICKETS_QUEUE)
                    .header(HEADER_USER_ROLE, "OPERATOR")
                    .header("X-User-Agence-Id", AGENCE_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_TOTAL_EN_ATTENTE).value(1))
                    .andExpect(jsonPath(JSON_TICKETS_0_CLIENT_NOM).value("Mon agence"));
        }
    }

    // ════════════════════════════════════════════════════════
    // FLUX COMPLET : Canal → File → Assignation
    // ════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Flux complet : Acquisition → File → Assignation")
    class FullFlowIT {

        @Test
        @DisplayName("Flux complet : appel VoIP → file d'attente → self-assign → EN_COURS")
        void fullFlow_call_queue_assign() throws Exception {
            // 1. Un appel VoIP arrive
            CallChannelRequest callReq = new CallChannelRequest();
            callReq.setCallerNumber("98777888");
            callReq.setCallerName("Flux Test");
            callReq.setAgenceId(AGENCE_ID);

            String createResponse = mockMvc.perform(post(API_CHANNELS_CALL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(callReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(JSON_STATUT).value(STATUT_NOUVEAU))
                    .andReturn().getResponse().getContentAsString();

            String ticketId = objectMapper.readTree(createResponse).get("id").asText();

            // 2. Vérifier qu'il est dans la file d'attente
            mockMvc.perform(get(API_TICKETS_QUEUE)
                    .header(HEADER_USER_ROLE, ROLE_ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_TOTAL_EN_ATTENTE).value(1))
                    .andExpect(jsonPath(JSON_TICKETS_0_CLIENT_NOM).value("Flux Test"));

            // 3. Un opérateur se self-assign
            UUID operatorId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            String assignBody = objectMapper.writeValueAsString(
                    new com.pfe.callmgmt.ticket.dto.AssignTicketRequest(operatorId));

            mockMvc.perform(patch("/api/tickets/{id}/assign", ticketId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", operatorId.toString())
                    .header(HEADER_USER_ROLE, "OPERATOR")
                    .content(assignBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_STATUT).value("EN_COURS"))
                    .andExpect(jsonPath(JSON_OPERATEUR_ID).value(operatorId.toString()));

            // 4. Vérifier qu'il n'est plus dans la file d'attente
            mockMvc.perform(get(API_TICKETS_QUEUE)
                    .header(HEADER_USER_ROLE, ROLE_ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(JSON_TOTAL_EN_ATTENTE).value(0));
        }
    }
}
