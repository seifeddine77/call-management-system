package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.InternalUserResponse;
import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.dto.UserResponse;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.entity.User;
import com.pfe.callmgmt.user.event.UserCreatedEvent;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.mapper.UserMapper;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import com.pfe.callmgmt.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation métier des utilisateurs : persistance, hachage BCrypt et <b>RBAC fin</b>.
 *
 * <p>Source de vérité des identifiants de connexion : l'auth-service lit le {@code passwordHash}
 * via Feign pour valider les logins. La méthode {@code validateUpdatePermissions} applique la règle
 * d'accès : un OPERATOR ne modifie que lui-même (et jamais son rôle), un SUPERVISOR uniquement
 * les comptes de son agence, un ADMIN n'importe qui.</p>
 */
@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String ROLE_SUPERVISOR = "SUPERVISOR";
    private static final String MSG_AGENCE_NOT_FOUND = "Agence non trouvée: ";
    private static final String MSG_USER_NOT_FOUND = "Utilisateur non trouvé: ";
    private static final String MSG_USER_EMAIL_NOT_FOUND = "Utilisateur non trouvé avec l'email: ";

    private final UserRepository userRepository;
    private final AgenceRepository agenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    public UserServiceImpl(UserRepository userRepository,
            AgenceRepository agenceRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            @org.springframework.lang.Nullable UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.agenceRepository = agenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UserResponse createUser(UserRequest request, UUID createdBy) {
        log.info("Creating user: {}", request.getEmail());

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Un utilisateur avec l'email " + request.getEmail() + " existe déjà");
        }

        // Build user entity
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .telephone(request.getTelephone())
                .role(request.getRole())
                .planning(request.getPlanning())
                .actif(true)
                .build();

        // CDC 1.A — Un OPERATOR ou SUPERVISOR DOIT être rattaché à une agence
        if ((ROLE_OPERATOR.equals(request.getRole()) || ROLE_SUPERVISOR.equals(request.getRole()))
                && request.getAgenceId() == null) {
            throw new IllegalArgumentException(
                    String.format("Un %s doit être rattaché à une agence", request.getRole()));
        }

        // Assign agence if provided
        if (request.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(request.getAgenceId())
                    .orElseThrow(() -> new EntityNotFoundException(MSG_AGENCE_NOT_FOUND + request.getAgenceId()));
            user.setAgence(agence);
        }

        User saved = userRepository.save(user);
        log.info("User created with ID: {}", saved.getId());

        // Publish event (if RabbitMQ is available)
        if (eventPublisher != null) {
            try {
                eventPublisher.publishUserCreated(UserCreatedEvent.builder()
                        .userId(saved.getId())
                        .email(saved.getEmail())
                        .nom(saved.getNom())
                        .prenom(saved.getPrenom())
                        .role(saved.getRole())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish UserCreatedEvent (RabbitMQ may be down): {}", e.getMessage(), e);
            }
        }

        return userMapper.toResponse(saved);
    }

    @Override
    public InternalUserResponse createUserInternal(UserRequest request) {
        log.info("[INTERNAL] Creating user from auth-service: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Un utilisateur avec l'email " + request.getEmail() + " existe déjà");
        }

        // Le mot de passe est DÉJÀ hashé par auth-service → on le stocke tel quel
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(request.getPassword()) // Already BCrypt-encoded by auth-service
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .telephone(request.getTelephone())
                .role(request.getRole())
                .planning(request.getPlanning())
                .actif(true)
                .build();

        // CDC 1.A — Un OPERATOR ou SUPERVISOR DOIT être rattaché à une agence
        if ((ROLE_OPERATOR.equals(request.getRole()) || ROLE_SUPERVISOR.equals(request.getRole()))
                && request.getAgenceId() == null) {
            throw new IllegalArgumentException(
                    String.format("Un %s doit être rattaché à une agence", request.getRole()));
        }

        if (request.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(request.getAgenceId())
                    .orElseThrow(() -> new EntityNotFoundException(MSG_AGENCE_NOT_FOUND + request.getAgenceId()));
            user.setAgence(agence);
        }

        User saved = userRepository.save(user);
        log.info("[INTERNAL] User created with ID: {}", saved.getId());

        // SonarCloud C1 fix — null check cohérent avec createUser() (eventPublisher est @Nullable)
        if (eventPublisher != null) {
            try {
                eventPublisher.publishUserCreated(UserCreatedEvent.builder()
                        .userId(saved.getId())
                        .email(saved.getEmail())
                        .nom(saved.getNom())
                        .prenom(saved.getPrenom())
                        .role(saved.getRole())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish UserCreatedEvent: {}", e.getMessage(), e);
            }
        }

        return toInternalResponse(saved);
    }

    /**
     * Met à jour un utilisateur sous contrôle d'accès (RBAC).
     * Le mot de passe n'est ré-haché que s'il est fourni et non vide ; l'email (identifiant de
     * connexion) n'est volontairement pas modifié ici.
     *
     * @param id                identifiant de l'utilisateur cible
     * @param request           nouvelles valeurs (nom, prénom, téléphone, rôle, agence…)
     * @param requesterId       identifiant du demandeur (issu du JWT)
     * @param requesterRole     rôle du demandeur (OPERATOR/SUPERVISOR/ADMIN)
     * @param requesterAgenceId agence du demandeur (borne la portée d'un SUPERVISOR)
     * @return l'utilisateur mis à jour
     * @throws org.springframework.security.access.AccessDeniedException si le demandeur n'est pas autorisé
     */
    @Override
    public UserResponse updateUser(UUID id, UserRequest request, UUID requesterId, String requesterRole,
            UUID requesterAgenceId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_NOT_FOUND + id));

        // RBAC enforcement — delegated to helper for clarity
        validateUpdatePermissions(id, request, user, requesterId, requesterRole, requesterAgenceId);

        // Update fields
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setTelephone(request.getTelephone());
        user.setPlanning(request.getPlanning());

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getAgenceId() != null) {
            Agence agence = agenceRepository.findById(request.getAgenceId())
                    .orElseThrow(() -> new EntityNotFoundException(MSG_AGENCE_NOT_FOUND + request.getAgenceId()));
            user.setAgence(agence);
        }

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    /**
     * Vérifie les droits RBAC pour la modification d'un utilisateur.
     * Un OPERATOR ne peut modifier que lui-même et ne peut pas changer de rôle.
     * Un SUPERVISOR ne peut modifier que les utilisateurs de son agence.
     * Un ADMIN peut modifier n'importe qui.
     */
    private void validateUpdatePermissions(UUID targetId, UserRequest request, User targetUser,
            UUID requesterId, String requesterRole, UUID requesterAgenceId) {
        if (ROLE_OPERATOR.equals(requesterRole)) {
            if (!targetId.equals(requesterId)) {
                throw new AccessDeniedException("Un opérateur ne peut modifier que son propre profil");
            }
            if (request.getRole() != null && !request.getRole().equals(targetUser.getRole())) {
                throw new AccessDeniedException("Un opérateur ne peut pas changer son rôle");
            }
        } else if (ROLE_SUPERVISOR.equals(requesterRole)) {
            UUID userAgenceId = targetUser.getAgence() != null ? targetUser.getAgence().getId() : null;
            if (userAgenceId != null && !userAgenceId.equals(requesterAgenceId)) {
                throw new AccessDeniedException("Un superviseur ne peut modifier que les utilisateurs de son agence");
            }
        }
        // ADMIN can update anyone — no restriction
    }

    @Override
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_NOT_FOUND + id));
        user.setActif(false);
        userRepository.save(user);
        log.info("User deactivated: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_NOT_FOUND + id));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_EMAIL_NOT_FOUND + email));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalUserResponse getUserByEmailInternal(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_EMAIL_NOT_FOUND + email));
        return toInternalResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String role, UUID agenceId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Par défaut, on ne retourne que les utilisateurs actifs
        Page<User> users;
        if (role != null && agenceId != null) {
            users = userRepository.findByActifTrueAndAgenceIdAndRole(agenceId, role, pageRequest);
        } else if (role != null) {
            users = userRepository.findByActifTrueAndRole(role, pageRequest);
        } else if (agenceId != null) {
            users = userRepository.findByActifTrueAndAgenceId(agenceId, pageRequest);
        } else {
            users = userRepository.findByActifTrue(pageRequest);
        }

        return users.map(userMapper::toResponse);
    }

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_NOT_FOUND + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);
    }

    @Override
    @Transactional
    public void updateLastLogin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_USER_NOT_FOUND + userId));
        user.setLastLogin(java.time.LocalDateTime.now());
        userRepository.save(user);
        log.info("Last login updated for user: {}", userId);
    }

    // ----- Helper -----
    private InternalUserResponse toInternalResponse(User user) {
        return InternalUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .telephone(user.getTelephone())
                .role(user.getRole())
                .agenceId(user.getAgence() != null ? user.getAgence().getId() : null)
                .actif(user.isActif())
                .build();
    }

    // ----- Inner exception class -----
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
