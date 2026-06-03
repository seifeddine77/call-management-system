package com.pfe.callmgmt.auth.service;

import com.pfe.callmgmt.auth.client.UserServiceClient;
import com.pfe.callmgmt.auth.dto.*;
import com.pfe.callmgmt.auth.entity.RefreshToken;
import com.pfe.callmgmt.auth.repository.RefreshTokenRepository;
import com.pfe.callmgmt.auth.util.JwtUtils;
import com.pfe.callmgmt.common.exception.ConflictException;
import com.pfe.callmgmt.common.exception.UnauthorizedException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

        // ── Test constants ────────────────────────────────────────
        private static final String OPERATOR_EMAIL    = "operator@test.com";
        private static final String PASSWORD_RAW      = "password123";
        private static final String PASSWORD_HASH     = "$2a$12$hashedPassword";
        private static final String ROLE_OPERATOR     = "OPERATOR";
        private static final String ACCESS_TOKEN      = "access-token-123";
        private static final String REFRESH_TOKEN     = "refresh-token-456";
        private static final String TOKEN_BEARER      = "Bearer";
        private static final String NEW_USER_EMAIL    = "new@test.com";
        private static final String REGISTER_ACCESS_TOKEN = "access-token";

        @Mock
        private RefreshTokenRepository refreshTokenRepository;

        @Mock
        private UserServiceClient userServiceClient;

        @Mock
        private JwtUtils jwtUtils;

        @Mock
        private PasswordEncoder passwordEncoder;

        @InjectMocks
        private AuthServiceImpl authService;

        private UserDto testUser;
        private UUID testUserId;

        @BeforeEach
        void setUp() {
                testUserId = UUID.randomUUID();
                testUser = UserDto.builder()
                                .id(testUserId)
                                .email(OPERATOR_EMAIL)
                                .passwordHash(PASSWORD_HASH)
                                .nom("Test Operator").prenom("")
                                .role(ROLE_OPERATOR)
                                .agenceId(UUID.randomUUID())
                                .actif(true)
                                .build();
        }

        // ─────────────────────────────────────────────────────
        // LOGIN TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("Login Tests")
        class LoginTests {

                @Test
                @DisplayName("Login avec des identifiants valides retourne AuthResponse")
                void login_success() {
                        // Given
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, PASSWORD_RAW);
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(testUser));
                        when(passwordEncoder.matches(PASSWORD_RAW, PASSWORD_HASH))
                                        .thenReturn(true);
                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any()))
                                        .thenReturn(ACCESS_TOKEN);
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any()))
                                        .thenReturn(REFRESH_TOKEN);
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);
                        when(refreshTokenRepository.save(any(RefreshToken.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        AuthResponse response = authService.login(request);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
                        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
                        assertThat(response.getTokenType()).isEqualTo(TOKEN_BEARER);
                        assertThat(response.getUser().getEmail()).isEqualTo(OPERATOR_EMAIL);
                        assertThat(response.getUser().getRole()).isEqualTo(ROLE_OPERATOR);

                        verify(refreshTokenRepository).save(any(RefreshToken.class));
                }

                @Test
                @DisplayName("Login avec un email inexistant lève UnauthorizedException")
                void login_userNotFound_throws() {
                        // Given
                        LoginRequest request = new LoginRequest("unknown@test.com", PASSWORD_RAW);
                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail("unknown@test.com"))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));

                        // When/Then
                        assertThatThrownBy(() -> authService.login(request))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Email ou mot de passe incorrect");
                }

                @Test
                @DisplayName("Login avec un mauvais mot de passe lève UnauthorizedException")
                void login_wrongPassword_throws() {
                        // Given
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, "wrongpassword");
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(testUser));
                        when(passwordEncoder.matches("wrongpassword", PASSWORD_HASH))
                                        .thenReturn(false);

                        // When/Then
                        assertThatThrownBy(() -> authService.login(request))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Email ou mot de passe incorrect");
                }

                @Test
                @DisplayName("Login avec un compte désactivé lève UnauthorizedException")
                void login_inactiveUser_throws() {
                        // Given
                        testUser.setActif(false);
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, PASSWORD_RAW);
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(testUser));

                        // When/Then
                        assertThatThrownBy(() -> authService.login(request))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessageContaining("désactivé");
                }

                @Test
                @DisplayName("Login avec FeignException lève ServiceUnavailableException")
                void login_feignException_throwsServiceUnavailable() {
                        // Given
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, PASSWORD_RAW);
                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenThrow(new FeignException.InternalServerError("Internal Server Error", feignRequest, null, null));

                        // When/Then
                        assertThatThrownBy(() -> authService.login(request))
                                        .isInstanceOf(com.pfe.callmgmt.common.exception.ServiceUnavailableException.class)
                                        .hasMessage("Service utilisateur indisponible");
                }

                @Test
                @DisplayName("Login avec utilisateur null lève UnauthorizedException")
                void login_userNull_throwsUnauthorized() {
                        // Given
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, PASSWORD_RAW);
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(null));

                        // When/Then
                        assertThatThrownBy(() -> authService.login(request))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Email ou mot de passe incorrect");
                }

                @Test
                @DisplayName("Login continue même si la mise à jour de lastLogin échoue")
                void login_updateLastLoginFails_logsWarning() {
                        // Given
                        LoginRequest request = new LoginRequest(OPERATOR_EMAIL, PASSWORD_RAW);
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(testUser));
                        when(passwordEncoder.matches(PASSWORD_RAW, PASSWORD_HASH))
                                        .thenReturn(true);
                        doThrow(new RuntimeException("DB error"))
                                        .when(userServiceClient).updateLastLogin(testUser.getId());
                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any()))
                                        .thenReturn(ACCESS_TOKEN);
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any()))
                                        .thenReturn(REFRESH_TOKEN);
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);
                        when(refreshTokenRepository.save(any(RefreshToken.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        // When
                        AuthResponse response = authService.login(request);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
                        verify(userServiceClient).updateLastLogin(testUser.getId());
                }
        }

        // ─────────────────────────────────────────────────────
        // REGISTER TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("Register Tests")
        class RegisterTests {

                @Test
                @DisplayName("Inscription réussie retourne AuthResponse avec tokens")
                void register_success() {
                        // Given
                        RegisterRequest request = new RegisterRequest();
                        request.setEmail(NEW_USER_EMAIL);
                        request.setPassword(PASSWORD_RAW);
                        request.setNom("Nouveau User"); /* prenom set below */
                        request.setRole(ROLE_OPERATOR);

                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(NEW_USER_EMAIL))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));
                        when(passwordEncoder.encode(PASSWORD_RAW)).thenReturn("$2a$12$encodedPassword");

                        UserDto createdUser = UserDto.builder()
                                        .id(UUID.randomUUID())
                                        .email(NEW_USER_EMAIL)
                                        .nom("Nouveau User").prenom("")
                                        .role(ROLE_OPERATOR)
                                        .actif(true)
                                        .build();
                        when(userServiceClient.createUser(any())).thenReturn(ResponseEntity.ok(createdUser));
                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any())).thenReturn(REGISTER_ACCESS_TOKEN);
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any()))
                                        .thenReturn("refresh-token");
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);
                        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        // When
                        AuthResponse response = authService.register(request);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getAccessToken()).isEqualTo(REGISTER_ACCESS_TOKEN);
                        assertThat(response.getUser().getEmail()).isEqualTo(NEW_USER_EMAIL);
                }

                @Test
                @DisplayName("Inscription avec email existant lève ConflictException")
                void register_emailExists_throws() {
                        // Given
                        RegisterRequest request = new RegisterRequest();
                        request.setEmail("existing@test.com");
                        request.setPassword(PASSWORD_RAW);
                        request.setNom("Existing User"); /* prenom set below */
                        request.setRole(ROLE_OPERATOR);

                        when(userServiceClient.findByEmail("existing@test.com"))
                                        .thenReturn(ResponseEntity.ok(testUser));

                        // When/Then
                        assertThatThrownBy(() -> authService.register(request))
                                        .isInstanceOf(ConflictException.class)
                                        .hasMessage("Un utilisateur avec cet email existe déjà");
                }

                @Test
                @DisplayName("Inscription avec FeignException sur la recherche email lève ServiceUnavailableException")
                void register_feignExceptionOnEmailCheck_throwsServiceUnavailable() {
                        // Given
                        RegisterRequest request = new RegisterRequest();
                        request.setEmail(NEW_USER_EMAIL);
                        request.setPassword(PASSWORD_RAW);
                        request.setNom("Nouveau User");
                        request.setRole(ROLE_OPERATOR);

                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(NEW_USER_EMAIL))
                                        .thenThrow(new FeignException.InternalServerError("Internal Error", feignRequest, null, null));

                        // When/Then
                        assertThatThrownBy(() -> authService.register(request))
                                        .isInstanceOf(com.pfe.callmgmt.common.exception.ServiceUnavailableException.class)
                                        .hasMessage("Service utilisateur indisponible");
                }

                @Test
                @DisplayName("Inscription avec FeignException lors de la création de l'utilisateur lève ServiceUnavailableException")
                void register_feignExceptionOnCreate_throwsServiceUnavailable() {
                        // Given
                        RegisterRequest request = new RegisterRequest();
                        request.setEmail(NEW_USER_EMAIL);
                        request.setPassword(PASSWORD_RAW);
                        request.setNom("Nouveau User");
                        request.setRole(ROLE_OPERATOR);

                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(NEW_USER_EMAIL))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));
                        when(passwordEncoder.encode(PASSWORD_RAW)).thenReturn("$2a$12$encodedPassword");
                        when(userServiceClient.createUser(any()))
                                        .thenThrow(new FeignException.InternalServerError("Internal Error", feignRequest, null, null));

                        // When/Then
                        assertThatThrownBy(() -> authService.register(request))
                                        .isInstanceOf(com.pfe.callmgmt.common.exception.ServiceUnavailableException.class)
                                        .hasMessage("Impossible de créer l'utilisateur");
                }

                @Test
                @DisplayName("Inscription avec réponse de création d'utilisateur null lève ServiceUnavailableException")
                void register_createdUserNull_throwsServiceUnavailable() {
                        // Given
                        RegisterRequest request = new RegisterRequest();
                        request.setEmail(NEW_USER_EMAIL);
                        request.setPassword(PASSWORD_RAW);
                        request.setNom("Nouveau User");
                        request.setRole(ROLE_OPERATOR);

                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(NEW_USER_EMAIL))
                                        .thenThrow(new FeignException.NotFound("Not Found", feignRequest, null, null));
                        when(passwordEncoder.encode(PASSWORD_RAW)).thenReturn("$2a$12$encodedPassword");
                        when(userServiceClient.createUser(any())).thenReturn(ResponseEntity.ok(null));

                        // When/Then
                        assertThatThrownBy(() -> authService.register(request))
                                        .isInstanceOf(com.pfe.callmgmt.common.exception.ServiceUnavailableException.class)
                                        .hasMessage("Impossible de créer l'utilisateur");
                }
        }

        // ─────────────────────────────────────────────────────
        // REFRESH TOKEN TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("Refresh Token Tests")
        class RefreshTokenTests {

                @Test
                @DisplayName("Refresh token valide génère de nouveaux tokens")
                @SuppressWarnings("unchecked")
                void refreshToken_success() {
                        // Given
                        String oldRefreshToken = "valid-refresh-token";

                        Jws<Claims> mockClaims = mock(Jws.class);

                        when(jwtUtils.validateRefreshToken(oldRefreshToken)).thenReturn(mockClaims);
                        when(jwtUtils.extractUserId(mockClaims)).thenReturn(testUserId);
                        when(jwtUtils.extractEmail(mockClaims)).thenReturn(OPERATOR_EMAIL);

                        RefreshToken storedToken = RefreshToken.builder()
                                        .id(UUID.randomUUID())
                                        .userId(testUserId)
                                        .token(oldRefreshToken)
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .revoked(false)
                                        .build();
                        when(refreshTokenRepository.findByToken(oldRefreshToken))
                                        .thenReturn(Optional.of(storedToken));
                        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenReturn(ResponseEntity.ok(testUser));

                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any()))
                                        .thenReturn("new-access-token");
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any()))
                                        .thenReturn("new-refresh-token");
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);

                        // When
                        AuthResponse response = authService.refreshToken(oldRefreshToken);

                        // Then
                        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
                        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
                        assertThat(storedToken.isRevoked()).isTrue(); // old token revoked
                }

        @Test
        @DisplayName("Refresh token invalide lève UnauthorizedException")
        void refreshToken_invalidToken_throws() {
            // Given
            when(jwtUtils.validateRefreshToken("invalid-token"))
                    .thenThrow(new RuntimeException("Invalid JWT"));

            // When/Then
            assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Refresh token invalide ou expiré");
        }

                @Test
                @DisplayName("Refresh token révoqué lève UnauthorizedException")
                @SuppressWarnings("unchecked")
                void refreshToken_revokedToken_throws() {
                        // Given
                        String revokedToken = "revoked-token";

                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(revokedToken)).thenReturn(mockClaims);

                        RefreshToken storedToken = RefreshToken.builder()
                                        .token(revokedToken)
                                        .revoked(true) // revoked
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .build();
                        when(refreshTokenRepository.findByToken(revokedToken))
                                        .thenReturn(Optional.of(storedToken));

                        // When/Then
                        assertThatThrownBy(() -> authService.refreshToken(revokedToken))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Refresh token révoqué");
                }

                @Test
                @DisplayName("Refresh token expiré en base lève UnauthorizedException")
                @SuppressWarnings("unchecked")
                void refreshToken_expiredInDb_throws() {
                        // Given
                        String expiredToken = "expired-token";

                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(expiredToken)).thenReturn(mockClaims);

                        RefreshToken storedToken = RefreshToken.builder()
                                        .token(expiredToken)
                                        .revoked(false)
                                        .expiresAt(LocalDateTime.now().minusDays(1)) // expired
                                        .build();
                        when(refreshTokenRepository.findByToken(expiredToken))
                                        .thenReturn(Optional.of(storedToken));

                        // When/Then
                        assertThatThrownBy(() -> authService.refreshToken(expiredToken))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Refresh token expiré");
                }

                @Test
                @DisplayName("Refresh token non trouvé en base lève UnauthorizedException")
                @SuppressWarnings("unchecked")
                void refreshToken_tokenNotFound_throwsUnauthorized() {
                        // Given
                        String tokenStr = "not-found-token";
                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(tokenStr)).thenReturn(mockClaims);
                        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.empty());

                        // When/Then
                        assertThatThrownBy(() -> authService.refreshToken(tokenStr))
                                        .isInstanceOf(UnauthorizedException.class)
                                        .hasMessage("Refresh token non trouvé");
                }

                @Test
                @DisplayName("Refresh token utilise les infos du token si le client Feign lève une exception")
                @SuppressWarnings("unchecked")
                void refreshToken_userServiceClientThrowsFeignException_fallsbackToTokenInfo() {
                        // Given
                        String tokenStr = "valid-token";
                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(tokenStr)).thenReturn(mockClaims);
                        when(jwtUtils.extractUserId(mockClaims)).thenReturn(testUserId);
                        when(jwtUtils.extractEmail(mockClaims)).thenReturn(OPERATOR_EMAIL);
                        when(jwtUtils.extractRole(mockClaims)).thenReturn(ROLE_OPERATOR);

                        RefreshToken storedToken = RefreshToken.builder()
                                        .userId(testUserId)
                                        .token(tokenStr)
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .revoked(false)
                                        .build();
                        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(storedToken));

                        Request feignRequest = Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
                                        new RequestTemplate());
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL))
                                        .thenThrow(new FeignException.InternalServerError("Internal Server Error", feignRequest, null, null));

                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any())).thenReturn("new-access");
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any())).thenReturn("new-refresh");
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);

                        // When
                        AuthResponse response = authService.refreshToken(tokenStr);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getAccessToken()).isEqualTo("new-access");
                        verify(refreshTokenRepository, times(2)).save(any());
                }

                @Test
                @DisplayName("Refresh token utilise les infos du token si le client Feign retourne un body null")
                @SuppressWarnings("unchecked")
                void refreshToken_userServiceClientReturnsNullBody_fallsbackToTokenInfo() {
                        // Given
                        String tokenStr = "valid-token";
                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(tokenStr)).thenReturn(mockClaims);
                        when(jwtUtils.extractUserId(mockClaims)).thenReturn(testUserId);
                        when(jwtUtils.extractEmail(mockClaims)).thenReturn(OPERATOR_EMAIL);
                        when(jwtUtils.extractRole(mockClaims)).thenReturn(ROLE_OPERATOR);

                        RefreshToken storedToken = RefreshToken.builder()
                                        .userId(testUserId)
                                        .token(tokenStr)
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .revoked(false)
                                        .build();
                        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(storedToken));
                        when(userServiceClient.findByEmail(OPERATOR_EMAIL)).thenReturn(ResponseEntity.ok(null));

                        when(jwtUtils.generateAccessToken(any(), anyString(), anyString(), any())).thenReturn("new-access");
                        when(jwtUtils.generateRefreshToken(any(), anyString(), anyString(), any())).thenReturn("new-refresh");
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);

                        // When
                        AuthResponse response = authService.refreshToken(tokenStr);

                        // Then
                        assertThat(response).isNotNull();
                        assertThat(response.getAccessToken()).isEqualTo("new-access");
                }

                @Test
                @DisplayName("Refresh token cherche par ID si l'email dans le token est absent ou null")
                @SuppressWarnings("unchecked")
                void refreshToken_userServiceClientReturnsNullEmailInClaims_fallsbackToId() {
                        // Given
                        String tokenStr = "valid-token";
                        Jws<Claims> mockClaims = mock(Jws.class);
                        when(jwtUtils.validateRefreshToken(tokenStr)).thenReturn(mockClaims);
                        when(jwtUtils.extractUserId(mockClaims)).thenReturn(testUserId);
                        when(jwtUtils.extractEmail(mockClaims)).thenReturn(null); // NULL email

                        RefreshToken storedToken = RefreshToken.builder()
                                        .userId(testUserId)
                                        .token(tokenStr)
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .revoked(false)
                                        .build();
                        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(storedToken));
                        
                        // ServiceClient will be called with the stringified userId as fallback
                        when(userServiceClient.findByEmail(testUserId.toString())).thenReturn(ResponseEntity.ok(testUser));

                        when(jwtUtils.generateAccessToken(any(), any(), anyString(), any())).thenReturn("new-access");
                        when(jwtUtils.generateRefreshToken(any(), any(), anyString(), any())).thenReturn("new-refresh");
                        when(jwtUtils.getAccessExpirationMs()).thenReturn(900000L);
                        when(jwtUtils.getRefreshExpirationMs()).thenReturn(604800000L);

                        // When
                        AuthResponse response = authService.refreshToken(tokenStr);

                        // Then
                        assertThat(response).isNotNull();
                        verify(userServiceClient).findByEmail(testUserId.toString());
                }
        }

        // ─────────────────────────────────────────────────────
        // LOGOUT TESTS
        // ─────────────────────────────────────────────────────
        @Nested
        @DisplayName("Logout Tests")
        class LogoutTests {

                @Test
                @DisplayName("Logout révoque le refresh token en base")
                void logout_success() {
                        // Given
                        String tokenStr = "some-refresh-token";
                        RefreshToken storedToken = RefreshToken.builder()
                                        .token(tokenStr)
                                        .revoked(false)
                                        .build();
                        when(refreshTokenRepository.findByToken(tokenStr))
                                        .thenReturn(Optional.of(storedToken));
                        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        // When
                        authService.logout(tokenStr);

                        // Then
                        assertThat(storedToken.isRevoked()).isTrue();
                        verify(refreshTokenRepository).save(storedToken);
                }

        @Test
        @DisplayName("Logout avec un token inconnu ne lève pas d'erreur")
        void logout_unknownToken_noError() {
            // Given
            when(refreshTokenRepository.findByToken("unknown-token"))
                    .thenReturn(Optional.empty());

            // When/Then — no exception
            authService.logout("unknown-token");
        }
        }
}
