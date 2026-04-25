package com.pfe.callmgmt.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgenceControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private AgenceRepository agenceRepository;

        @MockBean
        private UserEventPublisher userEventPublisher;

        @BeforeEach
        void cleanDb() {
                agenceRepository.deleteAll();
        }

        // ─────────────────────────────────────────────────────
        // CREATE AGENCE
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/agences — 201 Created")
        @Order(1)
        void createAgence_returns201() throws Exception {
                AgenceRequest request = new AgenceRequest();
                request.setNom("Agence Tunis");
                request.setAdresse("Av. Habib Bourguiba");
                request.setTelephone("71000111");
                request.setEmail("tunis@callmgmt.com");

                MvcResult result = mockMvc.perform(post("/api/agences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.nom").value("Agence Tunis"))
                                .andExpect(jsonPath("$.actif").value(true))
                                .andExpect(jsonPath("$.id").isNotEmpty())
                                .andReturn();

                assertThat(agenceRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("POST /api/agences — body invalide → 400")
        @Order(2)
        void createAgence_invalidBody_returns400() throws Exception {
                AgenceRequest request = new AgenceRequest();
                // nom is @NotBlank, so empty should fail

                mockMvc.perform(post("/api/agences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        // ─────────────────────────────────────────────────────
        // GET AGENCES
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /api/agences — 200 OK avec liste")
        @Order(3)
        void getAllAgences_returns200() throws Exception {
                // Seed one agence
                Agence agence = Agence.builder()
                                .nom("Agence Sfax")
                                .adresse("Rue Hedi Chaker")
                                .telephone("74111222")
                                .email("sfax@callmgmt.com")
                                .actif(true)
                                .build();
                agenceRepository.save(agence);

                mockMvc.perform(get("/api/agences"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].nom").value("Agence Sfax"));
        }

        @Test
        @DisplayName("GET /api/agences/{id} — 200 OK")
        @Order(4)
        void getAgenceById_returns200() throws Exception {
                Agence agence = agenceRepository.save(Agence.builder()
                                .nom("Agence Sousse").adresse("Centre ville").telephone("73000000")
                                .email("sousse@callmgmt.com").actif(true).build());

                mockMvc.perform(get("/api/agences/{id}", agence.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nom").value("Agence Sousse"));
        }

        @Test
        @DisplayName("GET /api/agences/{id} — non trouvée → 404")
        @Order(5)
        void getAgenceById_notFound_returns404() throws Exception {
                mockMvc.perform(get("/api/agences/{id}", java.util.UUID.randomUUID()))
                                .andExpect(status().isNotFound());
        }

        // ─────────────────────────────────────────────────────
        // UPDATE AGENCE
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("PUT /api/agences/{id} — 200 OK")
        @Order(6)
        void updateAgence_returns200() throws Exception {
                Agence agence = agenceRepository.save(Agence.builder()
                                .nom("Old Name").adresse("Old Address").telephone("70000000")
                                .email("old@callmgmt.com").actif(true).build());

                AgenceRequest update = new AgenceRequest();
                update.setNom("New Name");
                update.setAdresse("New Address");
                update.setTelephone("71111111");
                update.setEmail("new@callmgmt.com");

                mockMvc.perform(put("/api/agences/{id}", agence.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nom").value("New Name"));
        }

        // ─────────────────────────────────────────────────────
        // DEACTIVATE AGENCE
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("DELETE /api/agences/{id} — 204 No Content (soft delete)")
        @Order(7)
        void deactivateAgence_returns204() throws Exception {
                Agence agence = agenceRepository.save(Agence.builder()
                                .nom("To Deactivate").adresse("Address").telephone("70000000")
                                .email("deactivate@callmgmt.com").actif(true).build());

                mockMvc.perform(delete("/api/agences/{id}", agence.getId()))
                                .andExpect(status().isNoContent());

                Agence updated = agenceRepository.findById(agence.getId()).orElseThrow();
                assertThat(updated.isActif()).isFalse();
        }

        // ─────────────────────────────────────────────────────
        // STOCK DESCRIPTION (CDC Module 1 — nouveau champ)
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/agences — 201 avec stockDescription")
        @Order(8)
        void createAgence_withStock_returns201() throws Exception {
                AgenceRequest request = new AgenceRequest();
                request.setNom("Agence Stock Test");
                request.setAdresse("Av. Test");
                request.setTelephone("71555666");
                request.setEmail("stock@callmgmt.com");
                request.setStockDescription("Pièces mécaniques: 450 unités, Électriques: 120");

                mockMvc.perform(post("/api/agences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.stockDescription")
                                                .value("Pièces mécaniques: 450 unités, Électriques: 120"));
        }

        @Test
        @DisplayName("PUT /api/agences/{id} — mise à jour stockDescription")
        @Order(9)
        void updateAgence_stock_returns200() throws Exception {
                Agence agence = agenceRepository.save(Agence.builder()
                                .nom("Agence MàJ Stock").adresse("Old Addr").telephone("70000000")
                                .email("majstock@callmgmt.com").stockDescription("Ancien stock").actif(true).build());

                AgenceRequest update = new AgenceRequest();
                update.setNom("Agence MàJ Stock");
                update.setAdresse("New Addr");
                update.setTelephone("71111111");
                update.setEmail("majstock@callmgmt.com");
                update.setStockDescription("Nouveau stock: 500 pièces toutes catégories");

                mockMvc.perform(put("/api/agences/{id}", agence.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.stockDescription")
                                                .value("Nouveau stock: 500 pièces toutes catégories"));
        }
}
