package com.pfe.callmgmt.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.auth.client.UserServiceClient;
import com.pfe.callmgmt.auth.dto.*;
import com.pfe.callmgmt.auth.entity.RefreshToken;
import com.pfe.callmgmt.auth.repository.RefreshTokenRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Auth Controller — Integration Tests")
class AuthControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @MockBean
        private UserServiceClient userServiceClient;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private UserDto testUser;
        private UUID testUserId;

        @BeforeEach
        void setUp() {
                refreshTokenRepository.deleteAll();
                testUserId = UUID.randomUUID();
                testUser = UserDto.builder()
                                .id(testUserId)
                                .email("operator@test.com")
                                // Dynamically generate BCrypt hash for "password123"
                                .passwordHash(passwordEncoder.encode("password123"))
                                .nom("Test Operator").prenom("")
                                .role("OPERATOR")
                                .agenceId(UUID.randomUUID())
                                .actif(true)
                                .build();
        }

        // ─────────────────────────────────────────────────────
        // LOGIN INTEGRATION TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("POST /api/auth/login")
        class LoginIT {

        @Test
        @DisplayName("Login réussi retourne 200 avec access + refresh tokens")
        void login_success_returns200() throws Exception {
            when(userServiceClient.findByEmail("operator@test.com"))
                    .thenReturn(ResponseEntity.ok(testUser));

            LoginRequest request = new LoginRequest("operator@test.com", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.email").value("operator@test.com"))
                    .andExpect(jsonPath("$.user.role").value("OPERATOR"));

            // Verify refresh token is saved in DB
            assertThat(refreshTokenRepository.findByUserId(testUserId)).isNotEmpty();
        }

                @Test
                @DisplayName("Login avec email invalide retourne 400")
                void login_invalidEmail_returns400() throws Exception {
                        LoginRequest request = new LoginRequest("invalid-email", "password123");

                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
                }

        @Test
        @DisplayName("Login avec mauvais mot de passe retourne 401")
        void login_wrongPassword_returns401() throws Exception {
            when(userServiceClient.findByEmail("operator@test.com"))
                    .thenReturn(ResponseEntity.ok(testUser));

            LoginRequest request = new LoginRequest("operator@test.com", "wrongpassword");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Email ou mot de passe incorrect"));
        }

                @Test
                @DisplayName("Login avec utilisateur non trouvé retourne 401")
                void login_userNotFound_returns401() throws Exception {
                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail("unknown@test.com"))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));

                        LoginRequest request = new LoginRequest("unknown@test.com", "password123");

                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isUnauthorized());
                }

                @Test
                @DisplayName("Login sans body retourne 400")
                void login_emptyBody_returns400() throws Exception {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest());
                }
        }

        // ─────────────────────────────────────────────────────
        // REGISTER INTEGRATION TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("POST /api/auth/register")
        class RegisterIT {

                @Test
                @DisplayName("Inscription réussie retourne 201 avec tokens")
                void register_success_returns201() throws Exception {
                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail("new@test.com"))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));

                        UserDto createdUser = UserDto.builder()
                                        .id(UUID.randomUUID())
                                        .email("new@test.com")
                                        .nom("Nouveau User").prenom("")
                                        .role("OPERATOR")
                                        .actif(true)
                                        .build();
                        when(userServiceClient.createUser(any())).thenReturn(ResponseEntity.ok(createdUser));

                        RegisterRequest request = new RegisterRequest();
                        request.setEmail("new@test.com");
                        request.setPassword("password123");
                        request.setNom("Nouveau User"); /* prenom set below */;
                        request.setRole("OPERATOR");

                        mockMvc.perform(post("/api/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                                        .andExpect(jsonPath("$.user.email").value("new@test.com"));
                }

        @Test
        @DisplayName("Inscription avec email existant retourne 409")
        void register_emailExists_returns409() throws Exception {
            when(userServiceClient.findByEmail("existing@test.com"))
                    .thenReturn(ResponseEntity.ok(testUser));

            RegisterRequest request = new RegisterRequest();
            request.setEmail("existing@test.com");
            request.setPassword("password123");
            request.setNom("Existing User"); /* prenom set below */;
            request.setRole("OPERATOR");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Un utilisateur avec cet email existe déjà"));
        }
        }

        // ─────────────────────────────────────────────────────
        // REFRESH TOKEN INTEGRATION TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("POST /api/auth/refresh")
        class RefreshIT {

        @Test
        @DisplayName("Refresh token valide retourne de nouveaux tokens")
        void refreshToken_success_returns200() throws Exception {
            // First, login to get a valid refresh token
            when(userServiceClient.findByEmail("operator@test.com"))
                    .thenReturn(ResponseEntity.ok(testUser));

            LoginRequest loginRequest = new LoginRequest("operator@test.com", "password123");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse loginResponse = objectMapper.readValue(
                    loginResult.getResponse().getContentAsString(), AuthResponse.class);

            // Then, use the refresh token
            RefreshRequest refreshRequest = new RefreshRequest(loginResponse.getRefreshToken());

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        }

                @Test
                @DisplayName("Refresh token invalide retourne 401")
                void refreshToken_invalidToken_returns401() throws Exception {
                        RefreshRequest request = new RefreshRequest("totally-invalid-token");

                        mockMvc.perform(post("/api/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isUnauthorized());
                }
        }

        // ─────────────────────────────────────────────────────
        // LOGOUT INTEGRATION TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("POST /api/auth/logout")
        class LogoutIT {

        @Test
        @DisplayName("Logout réussi retourne 204")
        void logout_success_returns204() throws Exception {
            // First, login
            when(userServiceClient.findByEmail("operator@test.com"))
                    .thenReturn(ResponseEntity.ok(testUser));

            LoginRequest loginRequest = new LoginRequest("operator@test.com", "password123");

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse loginResponse = objectMapper.readValue(
                    loginResult.getResponse().getContentAsString(), AuthResponse.class);

            // Then, logout
            RefreshRequest logoutRequest = new RefreshRequest(loginResponse.getRefreshToken());

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(logoutRequest)))
                    .andExpect(status().isNoContent());

            // Verify token was revoked
            RefreshToken revokedToken = refreshTokenRepository
                    .findByToken(loginResponse.getRefreshToken())
                    .orElse(null);
            assertThat(revokedToken).isNotNull();
            assertThat(revokedToken.isRevoked()).isTrue();
        }
        }
}
