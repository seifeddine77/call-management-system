package com.pfe.callmgmt.user.controller;

import com.pfe.callmgmt.user.dto.ChangePasswordRequest;
import com.pfe.callmgmt.user.dto.InternalUserResponse;
import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.dto.UserResponse;
import com.pfe.callmgmt.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController — tests unitaires")
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UUID userId;
    private UUID agenceId;
    private UserResponse userResponse;
    // UserRequest(email, password, nom, prenom, telephone, role, agenceId, planning)
    private UserRequest userRequest;

    @BeforeEach
    void setUp() {
        userId   = UUID.randomUUID();
        agenceId = UUID.randomUUID();
        userResponse = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .nom("Dupont")
                .prenom("Jean")
                .role("OPERATEUR")
                .actif(true)
                .build();
        // UserRequest: email, password, nom, prenom, telephone, role, agenceId, planning
        userRequest = new UserRequest("test@example.com", null, "Dupont", "Jean",
                null, "OPERATEUR", agenceId, null);
    }

    // ─── GET /api/users ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users — ADMIN retourne tous les utilisateurs sans filtre agence")
    void getAllUsers_asAdmin_returnsAll() {
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse));
        when(userService.getAllUsers(null, null, 0, 20)).thenReturn(page);

        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(
                null, null, "ADMIN", agenceId, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        verify(userService).getAllUsers(null, null, 0, 20);
    }

    @Test
    @DisplayName("GET /api/users — SUPERVISOR sans agenceId filtre par son agence")
    void getAllUsers_asSupervisor_filtersByOwnAgence() {
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse));
        when(userService.getAllUsers(null, agenceId, 0, 20)).thenReturn(page);

        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(
                null, null, "SUPERVISOR", agenceId, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).getAllUsers(null, agenceId, 0, 20);
    }

    @Test
    @DisplayName("GET /api/users — SUPERVISOR avec agenceId explicite l'utilise tel quel")
    void getAllUsers_asSupervisor_withExplicitAgenceId_usesProvided() {
        UUID explicitAgenceId = UUID.randomUUID();
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse));
        when(userService.getAllUsers("OPERATEUR", explicitAgenceId, 0, 10)).thenReturn(page);

        ResponseEntity<Page<UserResponse>> response = userController.getAllUsers(
                "OPERATEUR", explicitAgenceId, "SUPERVISOR", agenceId, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).getAllUsers("OPERATEUR", explicitAgenceId, 0, 10);
    }

    // ─── GET /api/users/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{id} — retourne 200 avec l'utilisateur")
    void getUserById_returns200() {
        when(userService.getUserById(userId)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.getUserById(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        verify(userService).getUserById(userId);
    }

    // ─── GET /api/users/email/{email} ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/email/{email} — retourne InternalUserResponse avec passwordHash")
    void getUserByEmail_returnsInternalResponse() {
        // InternalUserResponse: id, email, passwordHash, nom, prenom, telephone, role, agenceId, actif
        InternalUserResponse internalResponse = InternalUserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role("OPERATEUR")
                .actif(true)
                .build();
        when(userService.getUserByEmailInternal("test@example.com")).thenReturn(internalResponse);

        ResponseEntity<InternalUserResponse> response =
                userController.getUserByEmail("test@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPasswordHash()).isEqualTo("$2a$10$hashedpassword");
        verify(userService).getUserByEmailInternal("test@example.com");
    }

    // ─── POST /api/users ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/users — retourne 201 Created")
    void createUser_returns201() {
        UUID requesterId = UUID.randomUUID();
        when(userService.createUser(userRequest, requesterId)).thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.createUser(userRequest, requesterId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        verify(userService).createUser(userRequest, requesterId);
    }

    // ─── POST /api/users/internal ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/users/internal — retourne 201 avec InternalUserResponse")
    void createUserInternal_returns201() {
        InternalUserResponse internalResponse = InternalUserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role("OPERATEUR")
                .actif(true)
                .build();
        when(userService.createUserInternal(userRequest)).thenReturn(internalResponse);

        ResponseEntity<InternalUserResponse> response = userController.createUserInternal(userRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        verify(userService).createUserInternal(userRequest);
    }

    // ─── PUT /api/users/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/users/{id} — retourne 200 avec l'utilisateur mis à jour")
    void updateUser_returns200() {
        UUID requesterId = UUID.randomUUID();
        when(userService.updateUser(userId, userRequest, requesterId, "ADMIN", agenceId))
                .thenReturn(userResponse);

        ResponseEntity<UserResponse> response = userController.updateUser(
                userId, userRequest, requesterId, "ADMIN", agenceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(userService).updateUser(userId, userRequest, requesterId, "ADMIN", agenceId);
    }

    // ─── DELETE /api/users/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/users/{id} — retourne 204 No Content")
    void deactivateUser_returns204() {
        doNothing().when(userService).deactivateUser(userId);

        ResponseEntity<Void> response = userController.deactivateUser(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).deactivateUser(userId);
    }

    // ─── PATCH /api/users/{id}/password ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/{id}/password — retourne 204 No Content")
    void changePassword_returns204() {
        // ChangePasswordRequest: @Data @NoArgsConstructor @AllArgsConstructor
        ChangePasswordRequest req = new ChangePasswordRequest("oldPass123", "newPass456");
        doNothing().when(userService).changePassword(userId, "oldPass123", "newPass456");

        ResponseEntity<Void> response = userController.changePassword(userId, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).changePassword(userId, "oldPass123", "newPass456");
    }

    // ─── PATCH /api/users/{id}/last-login ────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/{id}/last-login — retourne 204 No Content")
    void updateLastLogin_returns204() {
        doNothing().when(userService).updateLastLogin(userId);

        ResponseEntity<Void> response = userController.updateLastLogin(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).updateLastLogin(userId);
    }
}
