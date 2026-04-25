package com.pfe.callmgmt.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.user.dto.ChangePasswordRequest;
import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.entity.User;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import com.pfe.callmgmt.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AgenceRepository agenceRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @MockBean
        private UserEventPublisher userEventPublisher;

        private Agence testAgence;

        @BeforeEach
        void setUp() {
                userRepository.deleteAll();
                agenceRepository.deleteAll();
                testAgence = agenceRepository.save(Agence.builder()
                                .nom("Agence Test IT")
                                .adresse("Test Address")
                                .telephone("71000000")
                                .email("it@callmgmt.com")
                                .actif(true)
                                .build());
        }

        // ─────────────────────────────────────────────────────
        // CREATE USER
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/users — 201 Created")
        @Order(1)
        void createUser_returns201() throws Exception {
                UserRequest request = new UserRequest();
                request.setEmail("operator1@callmgmt.com");
                request.setPassword("SecurePass123");
                request.setNom("Test");
                request.setPrenom("Opérateur");
                request.setTelephone("98111222");
                request.setRole("OPERATOR");
                request.setAgenceId(testAgence.getId());

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.email").value("operator1@callmgmt.com"))
                                .andExpect(jsonPath("$.nom").value("Test"))
                                .andExpect(jsonPath("$.prenom").value("Opérateur"))
                                .andExpect(jsonPath("$.role").value("OPERATOR"))
                                .andExpect(jsonPath("$.actif").value(true));

                assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("POST /api/users — email dupliqué → 409")
        @Order(2)
        void createUser_duplicateEmail_returns409() throws Exception {
                // Seed an existing user
                userRepository.save(User.builder()
                                .email("dup@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("Dup User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                UserRequest request = new UserRequest();
                request.setEmail("dup@callmgmt.com");
                request.setPassword("AnotherPass1");
                request.setNom("User");
                request.setPrenom("Another");
                request.setRole("OPERATOR");
                request.setAgenceId(testAgence.getId());

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("POST /api/users — body invalide → 400")
        @Order(3)
        void createUser_invalidBody_returns400() throws Exception {
                UserRequest request = new UserRequest();
                // Missing all required fields

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        // ─────────────────────────────────────────────────────
        // GET USERS
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /api/users — 200 OK avec liste paginée")
        @Order(4)
        void getAllUsers_returns200() throws Exception {
                userRepository.save(User.builder()
                                .email("list@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("List User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                mockMvc.perform(get("/api/users"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].email").value("list@callmgmt.com"));
        }

        @Test
        @DisplayName("GET /api/users/{id} — 200 OK")
        @Order(5)
        void getUserById_returns200() throws Exception {
                User user = userRepository.save(User.builder()
                                .email("byid@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("ById User").prenom("")
                                .role("SUPERVISOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                mockMvc.perform(get("/api/users/{id}", user.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nom").value("ById User"))
                                .andExpect(jsonPath("$.role").value("SUPERVISOR"));
        }

        @Test
        @DisplayName("GET /api/users/{id} — non trouvé → 404")
        @Order(6)
        void getUserById_notFound_returns404() throws Exception {
                mockMvc.perform(get("/api/users/{id}", UUID.randomUUID()))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/users/email/{email} — 200 OK (endpoint interne avec passwordHash)")
        @Order(7)
        void getUserByEmail_returns200() throws Exception {
                userRepository.save(User.builder()
                                .email("internal@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("Internal User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                mockMvc.perform(get("/api/users/email/{email}", "internal@callmgmt.com"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value("internal@callmgmt.com"))
                                .andExpect(jsonPath("$.passwordHash").isNotEmpty());
        }

        // ─────────────────────────────────────────────────────
        // UPDATE USER
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("PUT /api/users/{id} — 200 OK")
        @Order(8)
        void updateUser_returns200() throws Exception {
                User user = userRepository.save(User.builder()
                                .email("update@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("Old Name").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                UserRequest update = new UserRequest();
                update.setEmail("update@callmgmt.com");
                update.setNom("New Name");
                update.setPrenom("Updated");
                update.setTelephone("99888777");
                update.setRole("OPERATOR");
                update.setAgenceId(testAgence.getId());

                mockMvc.perform(put("/api/users/{id}", user.getId())
                                .header("X-User-Id", user.getId().toString())
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nom").value("New Name"));
        }

        // ─────────────────────────────────────────────────────
        // DEACTIVATE USER
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("DELETE /api/users/{id} — 204 No Content (soft delete)")
        @Order(9)
        void deactivateUser_returns204() throws Exception {
                User user = userRepository.save(User.builder()
                                .email("deactivate@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("Deactivate User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                mockMvc.perform(delete("/api/users/{id}", user.getId()))
                                .andExpect(status().isNoContent());

                User updated = userRepository.findById(user.getId()).orElseThrow();
                assertThat(updated.isActif()).isFalse();
        }

        // ─────────────────────────────────────────────────────
        // CHANGE PASSWORD
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("PATCH /api/users/{id}/password — 204 No Content")
        @Order(10)
        void changePassword_returns204() throws Exception {
                String rawPassword = "OldPassword123";
                User user = userRepository.save(User.builder()
                                .email("pwdchange@callmgmt.com")
                                .passwordHash(passwordEncoder.encode(rawPassword))
                                .nom("Password User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .actif(true)
                                .build());

                ChangePasswordRequest request = new ChangePasswordRequest();
                request.setOldPassword(rawPassword);
                request.setNewPassword("NewPassword456");

                mockMvc.perform(patch("/api/users/{id}/password", user.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNoContent());

                // Verify password was actually changed
                User updatedUser = userRepository.findById(user.getId()).orElseThrow();
                assertThat(passwordEncoder.matches("NewPassword456", updatedUser.getPasswordHash())).isTrue();
        }

        // ─────────────────────────────────────────────────────
        // PLANNING (CDC Module 1 — nouveau champ)
        // ─────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/users — 201 avec planning")
        @Order(11)
        void createUser_withPlanning_returns201() throws Exception {
                UserRequest request = new UserRequest();
                request.setEmail("planning@callmgmt.com");
                request.setPassword("SecurePass123");
                request.setNom("Planning");
                request.setPrenom("Opérateur");
                request.setTelephone("98222333");
                request.setRole("OPERATOR");
                request.setAgenceId(testAgence.getId());
                request.setPlanning("Lundi-Vendredi 08:00-17:00, Samedi 09:00-13:00");

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.planning")
                                                .value("Lundi-Vendredi 08:00-17:00, Samedi 09:00-13:00"));
        }

        @Test
        @DisplayName("PUT /api/users/{id} — mise à jour planning")
        @Order(12)
        void updateUser_planning_returns200() throws Exception {
                User user = userRepository.save(User.builder()
                                .email("planupdate@callmgmt.com")
                                .passwordHash(passwordEncoder.encode("pass"))
                                .nom("Plan User").prenom("")
                                .role("OPERATOR")
                                .agence(testAgence)
                                .planning("Lundi-Vendredi 08:00-17:00")
                                .actif(true)
                                .build());

                UserRequest update = new UserRequest();
                update.setEmail("planupdate@callmgmt.com");
                update.setNom("Plan User");
                update.setPrenom("Updated");
                update.setRole("OPERATOR");
                update.setAgenceId(testAgence.getId());
                update.setPlanning("Lundi-Samedi 07:00-19:00");

                mockMvc.perform(put("/api/users/{id}", user.getId())
                                .header("X-User-Id", user.getId().toString())
                                .header("X-User-Role", "ADMIN")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.planning").value("Lundi-Samedi 07:00-19:00"));
        }
}
