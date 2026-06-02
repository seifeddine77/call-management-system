package com.pfe.callmgmt.user.controller;

import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.dto.AgenceResponse;
import com.pfe.callmgmt.user.service.AgenceService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgenceController — tests unitaires")
class AgenceControllerTest {

    @Mock
    private AgenceService agenceService;

    @InjectMocks
    private AgenceController agenceController;

    private UUID agenceId;
    private AgenceResponse agenceResponse;
    private AgenceRequest agenceRequest;

    @BeforeEach
    void setUp() {
        agenceId = UUID.randomUUID();
        // AgenceResponse: id, nom, adresse, telephone, email, responsableId, responsableName,
        //                 stockDescription, whatsappGroupId, actif, createdAt
        agenceResponse = AgenceResponse.builder()
                .id(agenceId)
                .nom("Agence Paris Centre")
                .telephone("0145678900")
                .actif(true)
                .build();
        // AgenceRequest: nom, adresse, telephone, email, stockDescription
        agenceRequest = new AgenceRequest("Agence Paris Centre", null, "0145678900", null, null);
    }

    @Test
    @DisplayName("GET /api/agences — retourne 200 avec une page d'agences")
    void getAllAgences_returnsPageOfAgences() {
        Page<AgenceResponse> page = new PageImpl<>(List.of(agenceResponse));
        when(agenceService.getAllAgences(0, 20)).thenReturn(page);

        ResponseEntity<Page<AgenceResponse>> response = agenceController.getAllAgences(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        verify(agenceService).getAllAgences(0, 20);
    }

    @Test
    @DisplayName("GET /api/agences/{id} — retourne 200 avec l'agence")
    void getAgenceById_returnsAgence() {
        when(agenceService.getAgenceById(agenceId)).thenReturn(agenceResponse);

        ResponseEntity<AgenceResponse> response = agenceController.getAgenceById(agenceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(agenceId);
        verify(agenceService).getAgenceById(agenceId);
    }

    @Test
    @DisplayName("POST /api/agences — retourne 201 Created")
    void createAgence_returns201() {
        UUID requesterId = UUID.randomUUID();
        when(agenceService.createAgence(agenceRequest, requesterId)).thenReturn(agenceResponse);

        ResponseEntity<AgenceResponse> response = agenceController.createAgence(agenceRequest, requesterId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getNom()).isEqualTo("Agence Paris Centre");
        verify(agenceService).createAgence(agenceRequest, requesterId);
    }

    @Test
    @DisplayName("POST /api/agences — sans requesterId accepte null")
    void createAgence_withNullRequesterId_returns201() {
        when(agenceService.createAgence(agenceRequest, null)).thenReturn(agenceResponse);

        ResponseEntity<AgenceResponse> response = agenceController.createAgence(agenceRequest, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(agenceService).createAgence(agenceRequest, null);
    }

    @Test
    @DisplayName("PUT /api/agences/{id} — retourne 200 avec l'agence mise à jour")
    void updateAgence_returns200() {
        when(agenceService.updateAgence(agenceId, agenceRequest)).thenReturn(agenceResponse);

        ResponseEntity<AgenceResponse> response = agenceController.updateAgence(agenceId, agenceRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(agenceService).updateAgence(agenceId, agenceRequest);
    }

    @Test
    @DisplayName("DELETE /api/agences/{id} — retourne 204 No Content")
    void deactivateAgence_returns204() {
        doNothing().when(agenceService).deactivateAgence(agenceId);

        ResponseEntity<Void> response = agenceController.deactivateAgence(agenceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(agenceService).deactivateAgence(agenceId);
    }

    @Test
    @DisplayName("PATCH /api/agences/{id}/responsable — assigne le responsable et retourne 200")
    void assignResponsable_returns200() {
        UUID userId = UUID.randomUUID();
        when(agenceService.assignResponsable(agenceId, userId)).thenReturn(agenceResponse);

        ResponseEntity<AgenceResponse> response = agenceController.assignResponsable(
                agenceId, Map.of("userId", userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(agenceService).assignResponsable(agenceId, userId);
    }

    @Test
    @DisplayName("PATCH /api/agences/{id}/whatsapp-group — met à jour le group ID et retourne 200")
    void updateWhatsappGroupId_returns200() {
        String groupId = "120363000000000001@g.us";
        doNothing().when(agenceService).updateWhatsappGroupId(agenceId, groupId);

        ResponseEntity<Void> response = agenceController.updateWhatsappGroupId(
                agenceId, Map.of("groupId", groupId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(agenceService).updateWhatsappGroupId(agenceId, groupId);
    }
}
