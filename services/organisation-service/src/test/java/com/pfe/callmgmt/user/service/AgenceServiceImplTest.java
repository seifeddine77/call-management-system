package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.dto.AgenceResponse;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.entity.User;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.mapper.AgenceMapper;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import com.pfe.callmgmt.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenceServiceImplTest {

    // --- SonarCloud: extracted string constants ---
    private static final String AGENCE_NOM = "Agence Test";
    private static final String AGENCE_ADRESSE = "123 Rue Test";
    private static final String AGENCE_TEL = "71234567";
    private static final String AGENCE_EMAIL = "agence@test.com";
    private static final String WHATSAPP_GROUP = "120363xxx@g.us";

    @Mock
    private AgenceRepository agenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AgenceMapper agenceMapper;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private AgenceServiceImpl agenceService;

    private UUID agenceId;
    private UUID userId;
    private Agence agence;
    private AgenceRequest agenceRequest;
    private AgenceResponse agenceResponse;
    private User user;

    @BeforeEach
    void setUp() {
        agenceId = UUID.randomUUID();
        userId = UUID.randomUUID();

        agence = Agence.builder()
                .id(agenceId)
                .nom(AGENCE_NOM)
                .adresse(AGENCE_ADRESSE)
                .telephone(AGENCE_TEL)
                .email(AGENCE_EMAIL)
                .actif(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        agenceRequest = new AgenceRequest();
        agenceRequest.setNom(AGENCE_NOM);
        agenceRequest.setAdresse(AGENCE_ADRESSE);
        agenceRequest.setTelephone(AGENCE_TEL);
        agenceRequest.setEmail(AGENCE_EMAIL);

        agenceResponse = new AgenceResponse();
        agenceResponse.setId(agenceId);
        agenceResponse.setNom(AGENCE_NOM);
        agenceResponse.setAdresse(AGENCE_ADRESSE);
        agenceResponse.setTelephone(AGENCE_TEL);
        agenceResponse.setEmail(AGENCE_EMAIL);
        agenceResponse.setActif(true);

        user = User.builder()
                .id(userId)
                .email("user@test.com")
                .nom("User Test").prenom("")
                .role("SUPERVISOR")
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("createAgence — succès")
    void createAgence_success() {
        when(agenceRepository.save(any(Agence.class))).thenReturn(agence);
        when(agenceMapper.toResponse(any(Agence.class))).thenReturn(agenceResponse);

        AgenceResponse result = agenceService.createAgence(agenceRequest, userId);

        assertNotNull(result);
        assertEquals(AGENCE_NOM, result.getNom());
        verify(agenceRepository).save(any(Agence.class));
    }

    @Test
    @DisplayName("updateAgence — succès")
    void updateAgence_success() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(agenceRepository.save(any(Agence.class))).thenReturn(agence);
        when(agenceMapper.toResponse(any(Agence.class))).thenReturn(agenceResponse);

        AgenceResponse result = agenceService.updateAgence(agenceId, agenceRequest);

        assertNotNull(result);
        verify(agenceRepository).findById(agenceId);
        verify(agenceRepository).save(any(Agence.class));
    }

    @Test
    @DisplayName("updateAgence — agence non trouvée → EntityNotFoundException")
    void updateAgence_notFound() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> agenceService.updateAgence(agenceId, agenceRequest));
    }

    @Test
    @DisplayName("getAgenceById — succès")
    void getAgenceById_success() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(agenceMapper.toResponse(any(Agence.class))).thenReturn(agenceResponse);

        AgenceResponse result = agenceService.getAgenceById(agenceId);

        assertNotNull(result);
        assertEquals(agenceId, result.getId());
    }

    @Test
    @DisplayName("getAgenceById — non trouvée → EntityNotFoundException")
    void getAgenceById_notFound() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> agenceService.getAgenceById(agenceId));
    }

    @Test
    @DisplayName("getAllAgences — retourne liste paginée")
    void getAllAgences_returnsPaged() {
        Page<Agence> page = new PageImpl<>(List.of(agence));
        when(agenceRepository.findByActifTrue(any(Pageable.class))).thenReturn(page);
        when(agenceMapper.toResponse(any(Agence.class))).thenReturn(agenceResponse);

        Page<AgenceResponse> result = agenceService.getAllAgences(0, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("deactivateAgence — met actif à false")
    void deactivateAgence_setsActifFalse() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(agenceRepository.save(any(Agence.class))).thenReturn(agence);

        agenceService.deactivateAgence(agenceId);

        assertFalse(agence.isActif());
        verify(agenceRepository).save(agence);
    }

    @Test
    @DisplayName("assignResponsable — succès avec SUPERVISOR")
    void assignResponsable_success() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(agenceRepository.save(any(Agence.class))).thenReturn(agence);
        when(agenceMapper.toResponse(any(Agence.class))).thenReturn(agenceResponse);

        AgenceResponse result = agenceService.assignResponsable(agenceId, userId);

        assertNotNull(result);
        assertEquals(userId, agence.getResponsableId());
        verify(agenceRepository).save(agence);
    }

    @Test
    @DisplayName("assignResponsable — rôle ADMIN interdit → IllegalArgumentException")
    void assignResponsable_invalidRole() {
        user.setRole("ADMIN");
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> agenceService.assignResponsable(agenceId, userId));
    }

    @Test
    @DisplayName("updateWhatsappGroupId — succès")
    void updateWhatsappGroupId_success() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(agenceRepository.save(any(Agence.class))).thenReturn(agence);

        agenceService.updateWhatsappGroupId(agenceId, WHATSAPP_GROUP);

        assertEquals(WHATSAPP_GROUP, agence.getWhatsappGroupId());
        verify(agenceRepository).save(agence);
    }

    @Test
    @DisplayName("updateWhatsappGroupId — agence non trouvée → EntityNotFoundException")
    void updateWhatsappGroupId_notFound() {
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> agenceService.updateWhatsappGroupId(agenceId, WHATSAPP_GROUP));
    }
}
