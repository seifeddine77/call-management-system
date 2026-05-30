package com.pfe.callmgmt.auth.client;

import com.pfe.callmgmt.auth.dto.RegisterRequest;
import com.pfe.callmgmt.auth.dto.UserDto;
import com.pfe.callmgmt.auth.entity.LocalUser;
import com.pfe.callmgmt.auth.repository.LocalUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fallback factory pour UserServiceClient.
 * Utilisé quand organisation-service est indisponible (ex: en cours de développement).
 * Stocke/récupère les utilisateurs dans la table local_users de auth_db.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private final LocalUserRepository localUserRepository;

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("UserServiceClient fallback activé (organisation-service indisponible): {}", cause.getMessage(), cause);
        return new UserServiceClient() {

            @Override
            public ResponseEntity<UserDto> findByEmail(String email) {
                log.info("[FALLBACK] Recherche utilisateur local: {}", email);
                Optional<LocalUser> localUser = localUserRepository.findByEmail(email);
                if (localUser.isEmpty()) {
                    log.warn("[FALLBACK] Utilisateur non trouvé: {}", email);
                    return ResponseEntity.notFound().build();
                }
                LocalUser u = localUser.get();
                return ResponseEntity.ok(UserDto.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .passwordHash(u.getPasswordHash())
                        .nom(u.getNom())
                        .prenom(u.getPrenom())
                        .telephone(u.getTelephone())
                        .role(u.getRole())
                        .agenceId(u.getAgenceId())
                        .actif(u.isActif())
                        .build());
            }

            @Override
            public ResponseEntity<UserDto> createUser(RegisterRequest request) {
                log.info("[FALLBACK] Création utilisateur local: {}", request.getEmail());

                // Vérifier si l'email existe déjà
                if (localUserRepository.existsByEmail(request.getEmail())) {
                    log.warn("[FALLBACK] Email déjà utilisé: {}", request.getEmail());
                    // Return 409 - will be handled by auth-service
                    return ResponseEntity.status(409).build();
                }

                // Créer l'utilisateur dans local_users
                LocalUser newUser = LocalUser.builder()
                        .email(request.getEmail())
                        .passwordHash(request.getPassword()) // Already encoded by AuthServiceImpl
                        .nom(request.getNom())
                        .prenom(request.getPrenom())
                        .telephone(request.getTelephone())
                        .role(request.getRole())
                        .agenceId(request.getAgenceId())
                        .actif(true)
                        .build();

                LocalUser saved = localUserRepository.save(newUser);
                log.info("[FALLBACK] Utilisateur créé localement avec ID: {}", saved.getId());

                return ResponseEntity.status(201).body(UserDto.builder()
                        .id(saved.getId())
                        .email(saved.getEmail())
                        .passwordHash(saved.getPasswordHash())
                        .nom(saved.getNom())
                        .prenom(saved.getPrenom())
                        .telephone(saved.getTelephone())
                        .role(saved.getRole())
                        .agenceId(saved.getAgenceId())
                        .actif(saved.isActif())
                        .build());
            }

            @Override
            public ResponseEntity<Void> updateLastLogin(java.util.UUID id) {
                log.warn("[FALLBACK] Cannot update lastLogin — organisation-service unavailable");
                return ResponseEntity.noContent().build();
            }
        };
    }
}
