package com.pfe.callmgmt.rules.controller;

import com.pfe.callmgmt.rules.dto.ReferenceItemRequest;
import com.pfe.callmgmt.rules.entity.ReferenceItem;
import com.pfe.callmgmt.rules.repository.ReferenceItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST pour le paramétrage des listes de référence (CDC 4.B)
 * 
 * Accessible aux Superviseurs et Admins pour gérer dynamiquement :
 * - Types de pièces garanties
 * - Catégories de clients
 * - Types de sinistres (y compris exclusions)
 */
@RestController
@RequestMapping("/api/rules/reference-items")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Paramétrage Références", description = "CDC 4.B — CRUD des listes de référence")
public class ReferenceItemController {

    private static final String MSG_ITEM_NOT_FOUND = "Élément non trouvé: ";

    private final ReferenceItemRepository repository;

    /**
     * Lister tous les éléments (optionnel : par catégorie, inclure inactifs)
     */
    @GetMapping
    @Operation(summary = "Lister les éléments de référence")
    public ResponseEntity<List<ReferenceItem>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        List<ReferenceItem> items;
        if (category != null && !category.isBlank()) {
            items = includeInactive
                    ? repository.findByCategoryOrderByDisplayOrderAsc(category)
                    : repository.findByCategoryAndActifTrueOrderByDisplayOrderAsc(category);
        } else {
            items = includeInactive
                    ? repository.findAll()
                    : repository.findAll().stream().filter(ReferenceItem::getActif).toList();
        }
        return ResponseEntity.ok(items);
    }

    /**
     * Créer un nouvel élément de référence
     */
    @PostMapping
    @Operation(summary = "Ajouter un élément de référence",
            description = "Ajouter un type de pièce, catégorie client, ou type de sinistre")
    public ResponseEntity<ReferenceItem> create(@Valid @RequestBody ReferenceItemRequest request) {
        if (repository.existsByCategoryAndCode(request.getCategory(), request.getCode().toUpperCase())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ReferenceItem item = ReferenceItem.builder()
                .category(request.getCategory().toUpperCase())
                .code(request.getCode().toUpperCase())
                .label(request.getLabel())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .actif(true)
                .build();

        item = repository.save(item);
        log.info("✅ Référence créée: {} / {} ({})", item.getCategory(), item.getCode(), item.getLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    /**
     * Modifier un élément de référence
     */
    @PutMapping("/{id}")
    @Operation(summary = "Modifier un élément de référence")
    public ResponseEntity<ReferenceItem> update(@PathVariable UUID id,
                                                 @Valid @RequestBody ReferenceItemRequest request) {
        ReferenceItem item = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ITEM_NOT_FOUND + id));

        item.setLabel(request.getLabel());
        item.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }
        item = repository.save(item);
        log.info("✅ Référence modifiée: {} / {} → '{}'", item.getCategory(), item.getCode(), item.getLabel());
        return ResponseEntity.ok(item);
    }

    /**
     * Activer/désactiver un élément
     */
    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activer ou désactiver un élément de référence")
    public ResponseEntity<ReferenceItem> toggle(@PathVariable UUID id) {
        ReferenceItem item = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ITEM_NOT_FOUND + id));

        item.setActif(!Boolean.TRUE.equals(item.getActif()));
        item = repository.save(item);
        log.info("🔄 Référence {} / {} → {}", item.getCategory(), item.getCode(),
                Boolean.TRUE.equals(item.getActif()) ? "ACTIVÉE" : "DÉSACTIVÉE");
        return ResponseEntity.ok(item);
    }

    /**
     * Supprimer un élément de référence
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un élément de référence")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ReferenceItem item = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_ITEM_NOT_FOUND + id));
        repository.delete(item);
        log.info("🗑️ Référence supprimée: {} / {}", item.getCategory(), item.getCode());
        return ResponseEntity.noContent().build();
    }
}
