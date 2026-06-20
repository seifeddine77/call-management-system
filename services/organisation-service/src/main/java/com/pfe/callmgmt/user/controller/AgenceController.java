package com.pfe.callmgmt.user.controller;

import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.dto.AgenceResponse;
import com.pfe.callmgmt.user.service.AgenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@Tag(name = "Agences", description = "Gestion des agences")
public class AgenceController {

    private final AgenceService agenceService;

    @GetMapping
    @Operation(summary = "Lister toutes les agences actives")
    public ResponseEntity<Page<AgenceResponse>> getAllAgences(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(agenceService.getAllAgences(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une agence par ID")
    public ResponseEntity<AgenceResponse> getAgenceById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(agenceService.getAgenceById(id));
    }

    @PostMapping
    @Operation(summary = "Créer une agence (ADMIN uniquement)")
    public ResponseEntity<AgenceResponse> createAgence(
            @Valid @RequestBody AgenceRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID requesterId,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agenceService.createAgence(request, requesterId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une agence (ADMIN uniquement)")
    public ResponseEntity<AgenceResponse> updateAgence(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AgenceRequest request,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        return ResponseEntity.ok(agenceService.updateAgence(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactiver une agence (soft delete — ADMIN uniquement)")
    public ResponseEntity<Void> deactivateAgence(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        agenceService.deactivateAgence(id);
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

    @PatchMapping("/{id}/responsable")
    @Operation(summary = "Assigner un responsable à une agence")
    public ResponseEntity<AgenceResponse> assignResponsable(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, UUID> body,
            @RequestHeader(name = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        UUID userId = body.get("userId");
        return ResponseEntity.ok(agenceService.assignResponsable(id, userId));
    }

    @PatchMapping("/{id}/whatsapp-group")
    @Operation(summary = "Mettre à jour l'ID du groupe WhatsApp (interne)")
    public ResponseEntity<Void> updateWhatsappGroupId(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        String groupId = body.get("groupId");
        agenceService.updateWhatsappGroupId(id, groupId);
        return ResponseEntity.ok().build();
    }
}
