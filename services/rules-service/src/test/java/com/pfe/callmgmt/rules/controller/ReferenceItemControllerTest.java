package com.pfe.callmgmt.rules.controller;

import com.pfe.callmgmt.rules.dto.ReferenceItemRequest;
import com.pfe.callmgmt.rules.entity.ReferenceItem;
import com.pfe.callmgmt.rules.repository.ReferenceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferenceItemController — tests unitaires")
class ReferenceItemControllerTest {

    @Mock
    private ReferenceItemRepository repository;

    @InjectMocks
    private ReferenceItemController controller;

    private ReferenceItem activeItem;
    private ReferenceItem inactiveItem;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();

        activeItem = ReferenceItem.builder()
                .id(itemId)
                .category("TYPE_PIECE")
                .code("MECANIQUE")
                .label("Mécanique")
                .description("Pièces mécaniques")
                .displayOrder(1)
                .actif(true)
                .build();

        inactiveItem = ReferenceItem.builder()
                .id(UUID.randomUUID())
                .category("TYPE_PIECE")
                .code("CARROSSERIE")
                .label("Carrosserie")
                .actif(false)
                .build();
    }

    // ─── GET /api/rules/reference-items ──────────────────────────────────────

    @Nested
    @DisplayName("GET /api/rules/reference-items — list()")
    class ListTests {

        @Test
        @DisplayName("Sans catégorie, sans inactifs — retourne uniquement les actifs")
        void list_noCategory_activeOnly() {
            when(repository.findAll()).thenReturn(List.of(activeItem, inactiveItem));

            ResponseEntity<List<ReferenceItem>> response = controller.list(null, false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getCode()).isEqualTo("MECANIQUE");
        }

        @Test
        @DisplayName("Sans catégorie, includeInactive=true — retourne tout")
        void list_noCategory_includeInactive() {
            when(repository.findAll()).thenReturn(List.of(activeItem, inactiveItem));

            ResponseEntity<List<ReferenceItem>> response = controller.list(null, true);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("Avec catégorie, sans inactifs — appelle findByCategoryAndActifTrue")
        void list_withCategory_activeOnly() {
            when(repository.findByCategoryAndActifTrueOrderByDisplayOrderAsc("TYPE_PIECE"))
                    .thenReturn(List.of(activeItem));

            ResponseEntity<List<ReferenceItem>> response = controller.list("TYPE_PIECE", false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            verify(repository).findByCategoryAndActifTrueOrderByDisplayOrderAsc("TYPE_PIECE");
        }

        @Test
        @DisplayName("Avec catégorie, includeInactive=true — appelle findByCategory")
        void list_withCategory_includeInactive() {
            when(repository.findByCategoryOrderByDisplayOrderAsc("TYPE_PIECE"))
                    .thenReturn(List.of(activeItem, inactiveItem));

            ResponseEntity<List<ReferenceItem>> response = controller.list("TYPE_PIECE", true);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(repository).findByCategoryOrderByDisplayOrderAsc("TYPE_PIECE");
        }

        @Test
        @DisplayName("Catégorie vide (blank) — ignorée, retourne actifs de tout")
        void list_blankCategory_treatedAsNull() {
            when(repository.findAll()).thenReturn(List.of(activeItem));

            ResponseEntity<List<ReferenceItem>> response = controller.list("  ", false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(repository).findAll();
        }
    }

    // ─── POST /api/rules/reference-items ─────────────────────────────────────

    @Nested
    @DisplayName("POST /api/rules/reference-items — create()")
    class CreateTests {

        @Test
        @DisplayName("Création réussie — retourne 201 Created avec l'item")
        void create_success_returns201() {
            // Le controller applique .toUpperCase() sur category et code avant la vérification
            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("TYPE_PIECE")
                    .code("OPTIQUE")
                    .label("Pièces optiques")
                    .description("Phares, feux")
                    .displayOrder(3)
                    .build();

            when(repository.existsByCategoryAndCode("TYPE_PIECE", "OPTIQUE")).thenReturn(false);
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> {
                ReferenceItem saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ResponseEntity<ReferenceItem> response = controller.create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("OPTIQUE");
            assertThat(response.getBody().getCategory()).isEqualTo("TYPE_PIECE");
            assertThat(response.getBody().getActif()).isTrue();
            verify(repository).save(any(ReferenceItem.class));
        }

        @Test
        @DisplayName("Code déjà existant — retourne 409 Conflict")
        void create_duplicateCode_returns409() {
            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("TYPE_PIECE")
                    .code("MECANIQUE")
                    .label("Mécanique")
                    .build();

            when(repository.existsByCategoryAndCode("TYPE_PIECE", "MECANIQUE")).thenReturn(true);

            ResponseEntity<ReferenceItem> response = controller.create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNull();
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("displayOrder null dans la requête — défaut à 0")
        void create_nullDisplayOrder_defaultsToZero() {
            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("CATEGORIE_CLIENT")
                    .code("PREMIUM")
                    .label("Client Premium")
                    .displayOrder(null)
                    .build();

            when(repository.existsByCategoryAndCode("CATEGORIE_CLIENT", "PREMIUM")).thenReturn(false);
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<ReferenceItem> response = controller.create(request);

            assertThat(response.getBody().getDisplayOrder()).isEqualTo(0);
        }
    }

    // ─── PUT /api/rules/reference-items/{id} ─────────────────────────────────

    @Nested
    @DisplayName("PUT /api/rules/reference-items/{id} — update()")
    class UpdateTests {

        @Test
        @DisplayName("Mise à jour réussie — retourne 200 avec item modifié")
        void update_success_returns200() {
            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("TYPE_PIECE")
                    .code("MECANIQUE")
                    .label("Mécanique Avancée")
                    .description("Pièces mécaniques avancées")
                    .displayOrder(5)
                    .build();

            when(repository.findById(itemId)).thenReturn(Optional.of(activeItem));
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<ReferenceItem> response = controller.update(itemId, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getLabel()).isEqualTo("Mécanique Avancée");
            assertThat(response.getBody().getDisplayOrder()).isEqualTo(5);
            verify(repository).save(activeItem);
        }

        @Test
        @DisplayName("Item non trouvé — lève EntityNotFoundException")
        void update_notFound_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(repository.findById(unknownId)).thenReturn(Optional.empty());

            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("X").code("Y").label("Z").build();

            assertThatThrownBy(() -> controller.update(unknownId, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }

        @Test
        @DisplayName("displayOrder null dans update — ne modifie pas l'ordre")
        void update_nullDisplayOrder_doesNotChangeOrder() {
            activeItem.setDisplayOrder(10);
            ReferenceItemRequest request = ReferenceItemRequest.builder()
                    .category("TYPE_PIECE")
                    .code("MECANIQUE")
                    .label("Updated")
                    .displayOrder(null)
                    .build();

            when(repository.findById(itemId)).thenReturn(Optional.of(activeItem));
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<ReferenceItem> response = controller.update(itemId, request);

            assertThat(response.getBody().getDisplayOrder()).isEqualTo(10);
        }
    }

    // ─── PATCH /api/rules/reference-items/{id}/toggle ────────────────────────

    @Nested
    @DisplayName("PATCH /api/rules/reference-items/{id}/toggle — toggle()")
    class ToggleTests {

        @Test
        @DisplayName("Toggle item actif → devient inactif")
        void toggle_activeToInactive() {
            assertThat(activeItem.getActif()).isTrue();
            when(repository.findById(itemId)).thenReturn(Optional.of(activeItem));
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<ReferenceItem> response = controller.toggle(itemId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getActif()).isFalse();
            verify(repository).save(activeItem);
        }

        @Test
        @DisplayName("Toggle item inactif → devient actif")
        void toggle_inactiveToActive() {
            UUID inactiveId = inactiveItem.getId();
            assertThat(inactiveItem.getActif()).isFalse();
            when(repository.findById(inactiveId)).thenReturn(Optional.of(inactiveItem));
            when(repository.save(any(ReferenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<ReferenceItem> response = controller.toggle(inactiveId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getActif()).isTrue();
        }

        @Test
        @DisplayName("Toggle item non trouvé — lève EntityNotFoundException")
        void toggle_notFound_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(repository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.toggle(unknownId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }
    }

    // ─── DELETE /api/rules/reference-items/{id} ──────────────────────────────

    @Nested
    @DisplayName("DELETE /api/rules/reference-items/{id} — delete()")
    class DeleteTests {

        @Test
        @DisplayName("Suppression réussie — retourne 204 No Content")
        void delete_success_returns204() {
            when(repository.findById(itemId)).thenReturn(Optional.of(activeItem));
            doNothing().when(repository).delete(activeItem);

            ResponseEntity<Void> response = controller.delete(itemId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(repository).delete(activeItem);
        }

        @Test
        @DisplayName("Item non trouvé — lève EntityNotFoundException")
        void delete_notFound_throwsException() {
            UUID unknownId = UUID.randomUUID();
            when(repository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.delete(unknownId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }
    }
}
