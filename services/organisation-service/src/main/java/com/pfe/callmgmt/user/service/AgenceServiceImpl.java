package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.dto.AgenceResponse;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.entity.User;
import com.pfe.callmgmt.user.event.AgenceCreatedEvent;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.mapper.AgenceMapper;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import com.pfe.callmgmt.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AgenceServiceImpl implements AgenceService {

    private static final String AGENCE_NOT_FOUND = "Agence non trouvée: ";
    private static final String USER_NOT_FOUND = "Utilisateur non trouvé: ";

    private final AgenceRepository agenceRepository;
    private final UserRepository userRepository;
    private final AgenceMapper agenceMapper;
    private final UserEventPublisher eventPublisher;

    @Override
    public AgenceResponse createAgence(AgenceRequest request, UUID createdBy) {
        log.info("Creating agence: {}", request.getNom());

        Agence agence = Agence.builder()
                .nom(request.getNom())
                .adresse(request.getAdresse())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .stockDescription(request.getStockDescription())
                .actif(true)
                .build();

        Agence saved = agenceRepository.save(agence);
        log.info("Agence created with ID: {}", saved.getId());

        // Publier l'événement pour la création automatique du groupe WhatsApp
        try {
            eventPublisher.publishAgenceCreated(AgenceCreatedEvent.builder()
                    .agenceId(saved.getId().toString())
                    .nom(saved.getNom())
                    .telephone(saved.getTelephone())
                    .email(saved.getEmail())
                    .adresse(saved.getAdresse())
                    .build());
        } catch (Exception e) {
            log.warn("⚠️ Impossible de publier l'événement agence.created: {}", e.getMessage(), e);
        }

        return toResponseWithResponsableName(saved);
    }

    @Override
    public AgenceResponse updateAgence(UUID id, AgenceRequest request) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(AGENCE_NOT_FOUND + id));

        agence.setNom(request.getNom());
        agence.setAdresse(request.getAdresse());
        agence.setTelephone(request.getTelephone());
        agence.setEmail(request.getEmail());
        agence.setStockDescription(request.getStockDescription());

        Agence saved = agenceRepository.save(agence);
        return toResponseWithResponsableName(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AgenceResponse getAgenceById(UUID id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(AGENCE_NOT_FOUND + id));
        return toResponseWithResponsableName(agence);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgenceResponse> getAllAgences(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return agenceRepository.findByActifTrue(pageRequest).map(this::toResponseWithResponsableName);
    }

    @Override
    public void deactivateAgence(UUID id) {
        Agence agence = agenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(AGENCE_NOT_FOUND + id));
        agence.setActif(false);
        agenceRepository.save(agence);
        log.info("Agence deactivated: {}", id);
    }

    @Override
    public AgenceResponse assignResponsable(UUID agenceId, UUID userId) {
        Agence agence = agenceRepository.findById(agenceId)
                .orElseThrow(() -> new EntityNotFoundException(AGENCE_NOT_FOUND + agenceId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND + userId));

        // CDC 1.B — Seul un SUPERVISOR peut être responsable d'une agence
        if (!"SUPERVISOR".equals(user.getRole())) {
            throw new IllegalArgumentException(
                    "Le responsable d'une agence doit être un SUPERVISOR (CDC 1.B)");
        }

        agence.setResponsableId(userId);
        Agence saved = agenceRepository.save(agence);
        log.info("Responsable {} assigned to agence {}", userId, agenceId);
        return toResponseWithResponsableName(saved);
    }

    @Override
    public void updateWhatsappGroupId(UUID agenceId, String whatsappGroupId) {
        Agence agence = agenceRepository.findById(agenceId)
                .orElseThrow(() -> new EntityNotFoundException(AGENCE_NOT_FOUND + agenceId));
        agence.setWhatsappGroupId(whatsappGroupId);
        agenceRepository.save(agence);
        log.info("✅ WhatsApp Group ID saved for agence {}: {}", agence.getNom(), whatsappGroupId);
    }

    /**
     * Maps Agence to AgenceResponse including responsableName (from User table).
     */
    private AgenceResponse toResponseWithResponsableName(Agence agence) {
        AgenceResponse response = agenceMapper.toResponse(agence);
        if (agence.getResponsableId() != null) {
            userRepository.findById(agence.getResponsableId())
                    .ifPresent(user -> response.setResponsableName(user.getPrenom() + " " + user.getNom()));
        }
        return response;
    }
}
