package com.pfe.callmgmt.auth.controller;

import com.pfe.callmgmt.auth.dto.AuthResponse;
import com.pfe.callmgmt.auth.dto.LoginRequest;
import com.pfe.callmgmt.auth.dto.RefreshRequest;
import com.pfe.callmgmt.auth.dto.RegisterRequest;
import com.pfe.callmgmt.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — tests unitaires")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        authResponse = AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.access")
                .refreshToken("eyJhbGciOiJIUzI1NiJ9.refresh")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(AuthResponse.UserInfo.builder()
                        .id(UUID.randomUUID())
                        .email("admin@example.com")
                        .nom("Admin")
                        .prenom("Super")
                        .role("ADMIN")
                        .build())
                .build();
    }

    // ─── POST /api/auth/login ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login — retourne 200 avec tokens")
    void login_returnsTokens() {
        LoginRequest request = new LoginRequest("admin@example.com", "password123");
        when(authService.login(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getRefreshToken()).isNotBlank();
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        verify(authService).login(request);
    }

    @Test
    @DisplayName("POST /api/auth/login — retourne les infos utilisateur dans la réponse")
    void login_returnsUserInfo() {
        LoginRequest request = new LoginRequest("admin@example.com", "password123");
        when(authService.login(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertThat(response.getBody().getUser()).isNotNull();
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("admin@example.com");
        assertThat(response.getBody().getUser().getRole()).isEqualTo("ADMIN");
    }

    // ─── POST /api/auth/register ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register — retourne 201 Created avec tokens")
    void register_returns201() {
        RegisterRequest request = new RegisterRequest(
                "newuser@example.com", "securePass123",
                "Dupont", "Jean", "0145678900", "OPERATEUR", null);
        when(authService.register(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        verify(authService).register(request);
    }

    // ─── POST /api/auth/refresh ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh — retourne 200 avec nouveaux tokens")
    void refresh_returnsNewTokens() {
        String refreshToken = "eyJhbGciOiJIUzI1NiJ9.refresh";
        RefreshRequest request = new RefreshRequest(refreshToken);
        when(authService.refreshToken(refreshToken)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.refresh(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        verify(authService).refreshToken(refreshToken);
    }

    // ─── POST /api/auth/logout ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout — retourne 204 No Content")
    void logout_returns204() {
        String refreshToken = "eyJhbGciOiJIUzI1NiJ9.refresh";
        RefreshRequest request = new RefreshRequest(refreshToken);
        doNothing().when(authService).logout(refreshToken);

        ResponseEntity<Void> response = authController.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout(refreshToken);
    }

    // ─── GET /api/auth/health ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/health — retourne 200 OK")
    void health_returns200() {
        ResponseEntity<String> response = authController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
        verifyNoInteractions(authService);
    }
}
