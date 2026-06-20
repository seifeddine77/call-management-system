package com.pfe.callmgmt.user.controller;

import com.pfe.callmgmt.user.dto.ChangePasswordRequest;
import com.pfe.callmgmt.user.dto.InternalUserResponse;
import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.dto.UserResponse;
import com.pfe.callmgmt.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestion des utilisateurs")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lister les utilisateurs avec filtres")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "agenceId", required = false) UUID agenceId,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID requesterAgenceId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        // CDC 1.C — SUPERVISOR ne voit que les utilisateurs de son agence
        UUID effectiveAgenceId = agenceId;
        if ("SUPERVISOR".equals(requesterRole) && effectiveAgenceId == null) {
            effectiveAgenceId = requesterAgenceId;
        }
        return ResponseEntity.ok(userService.getAllUsers(role, effectiveAgenceId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un utilisateur par ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Obtenir un utilisateur par email (endpoint interne — retourne passwordHash)")
    public ResponseEntity<InternalUserResponse> getUserByEmail(@PathVariable("email") String email) {
        return ResponseEntity.ok(userService.getUserByEmailInternal(email));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur (ADMIN uniquement)")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID requesterId,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request, requesterId));
    }

    @PostMapping("/internal")
    @Operation(summary = "Créer un utilisateur (endpoint interne pour auth-service — mot de passe pré-hashé)")
    public ResponseEntity<InternalUserResponse> createUserInternal(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUserInternal(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UserRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID requesterId,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID requesterAgenceId) {
        return ResponseEntity.ok(userService.updateUser(id, request, requesterId, requesterRole, requesterAgenceId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactiver un utilisateur (soft delete — ADMIN uniquement)")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Contrôle d'accès : réserve l'action aux administrateurs.
     * Le rôle provient du JWT, injecté par l'API Gateway dans l'en-tête X-User-Role.
     */
    private static void requireAdmin(String requesterRole) {
        if (!"ADMIN".equals(requesterRole)) {
            throw new AccessDeniedException("Action réservée aux administrateurs");
        }
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Changer le mot de passe")
    public ResponseEntity<Void> changePassword(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/last-login")
    @Operation(summary = "Mettre à jour le timestamp de dernière connexion (endpoint interne)")
    public ResponseEntity<Void> updateLastLogin(@PathVariable("id") UUID id) {
        userService.updateLastLogin(id);
        return ResponseEntity.noContent().build();
    }
}
