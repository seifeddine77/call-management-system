package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.AgenceRequest;
import com.pfe.callmgmt.user.dto.AgenceResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AgenceService {
    AgenceResponse createAgence(AgenceRequest request, UUID createdBy);

    AgenceResponse updateAgence(UUID id, AgenceRequest request);

    AgenceResponse getAgenceById(UUID id);

    Page<AgenceResponse> getAllAgences(int page, int size);

    void deactivateAgence(UUID id);

    AgenceResponse assignResponsable(UUID agenceId, UUID userId);

    void updateWhatsappGroupId(UUID agenceId, String whatsappGroupId);
}
