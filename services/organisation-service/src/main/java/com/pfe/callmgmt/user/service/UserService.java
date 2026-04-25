package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.InternalUserResponse;
import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.dto.UserResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UserService {
    UserResponse createUser(UserRequest request, UUID createdBy);

    /** Endpoint interne: le mot de passe est DÉJÀ hashé par auth-service */
    InternalUserResponse createUserInternal(UserRequest request);

    UserResponse updateUser(UUID id, UserRequest request, UUID requesterId, String requesterRole,
            UUID requesterAgenceId);

    void deactivateUser(UUID id);

    UserResponse getUserById(UUID id);

    UserResponse getUserByEmail(String email);

    /** Endpoint interne: retourne le passwordHash pour auth-service */
    InternalUserResponse getUserByEmailInternal(String email);

    Page<UserResponse> getAllUsers(String role, UUID agenceId, int page, int size);

    void changePassword(UUID userId, String oldPassword, String newPassword);

    /** Met à jour le timestamp de dernière connexion */
    void updateLastLogin(UUID userId);
}
