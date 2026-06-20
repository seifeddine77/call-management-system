package com.pfe.callmgmt.auth.service;

import com.pfe.callmgmt.auth.client.UserServiceClient;
import com.pfe.callmgmt.auth.dto.*;
import com.pfe.callmgmt.auth.entity.RefreshToken;
import com.pfe.callmgmt.auth.repository.RefreshTokenRepository;
import com.pfe.callmgmt.auth.util.JwtUtils;
import com.pfe.callmgmt.common.exception.ConflictException;
import com.pfe.callmgmt.common.exception.ServiceUnavailableException;
import com.pfe.callmgmt.common.exception.UnauthorizedException;
import feign.FeignException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserServiceClient userServiceClient;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // 1. Trouver l'utilisateur via le organisation-service
        UserDto user;
        try {
            ResponseEntity<UserDto> response = userServiceClient.findByEmail(request.getEmail());
            user = response.getBody();
        } catch (FeignException.NotFound e) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        } catch (FeignException e) {
            log.error("Error calling organisation-service: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Service utilisateur indisponible");
        }

        if (user == null) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }

        // 2. Vérifier si l'utilisateur est actif
        if (!user.isActif()) {
            throw new UnauthorizedException("Compte désactivé. Contactez votre administrateur.");
        }

        // 3. Vérifier le mot de passe
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }

        // 4. Mettre à jour le timestamp de dernière connexion (non bloquant)
        try {
            userServiceClient.updateLastLogin(user.getId());
            log.info("Last login updated for user: {}", user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to update lastLogin for {}: {}", user.getEmail(), e.getMessage(), e);
        }

        // 5. Générer les tokens
        return generateTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // 1. Vérifier si l'email existe déjà
        try {
            ResponseEntity<UserDto> existing = userServiceClient.findByEmail(request.getEmail());
            if (existing.getBody() != null) {
                throw new ConflictException("Un utilisateur avec cet email existe déjà");
            }
        } catch (FeignException.NotFound e) {
            // OK — l'utilisateur n'existe pas
        } catch (ConflictException e) {
            throw e;
        } catch (FeignException e) {
            log.error("Error calling organisation-service: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Service utilisateur indisponible");
        }

        // 2. Sécurité : l'inscription publique ne peut JAMAIS attribuer un rôle privilégié.
        // On force OPERATOR quel que soit le rôle envoyé (anti-escalade de privilèges).
        // La création de comptes ADMIN/SUPERVISOR passe par POST /api/users (ADMIN authentifié).
        request.setRole("OPERATOR");

        // 3. Hasher le mot de passe et envoyer la création au organisation-service
        request.setPassword(passwordEncoder.encode(request.getPassword()));

        UserDto createdUser;
        try {
            ResponseEntity<UserDto> response = userServiceClient.createUser(request);
            createdUser = response.getBody();
        } catch (FeignException e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            throw new ServiceUnavailableException("Impossible de créer l'utilisateur");
        }

        if (createdUser == null) {
            throw new ServiceUnavailableException("Impossible de créer l'utilisateur");
        }

        log.info("User registered successfully: {}", createdUser.getEmail());

        // 3. Générer les tokens
        return generateTokens(createdUser);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        // 1. Valider le refresh token JWT
        Jws<Claims> claims;
        try {
            claims = jwtUtils.validateRefreshToken(refreshTokenStr);
        } catch (Exception e) {
            throw new UnauthorizedException("Refresh token invalide ou expiré");
        }

        // 2. Vérifier en base de données
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new UnauthorizedException("Refresh token non trouvé"));

        if (storedToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token révoqué");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expiré");
        }

        // 3. Révoquer l'ancien refresh token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // 4. Récupérer l'utilisateur
        UUID userId = jwtUtils.extractUserId(claims);
        UserDto user;
        try {
            ResponseEntity<UserDto> response = userServiceClient.findByEmail(
                    jwtUtils.extractEmail(claims) != null ? jwtUtils.extractEmail(claims) : userId.toString());
            user = response.getBody();
            if (user == null) {
                // Fallback: try to find by ID info from token
                user = UserDto.builder()
                        .id(userId)
                        .email(jwtUtils.extractEmail(claims))
                        .role(jwtUtils.extractRole(claims))
                        .build();
            }
        } catch (FeignException e) {
            // If user-service unavailable, use info from token
            user = UserDto.builder()
                    .id(userId)
                    .email(jwtUtils.extractEmail(claims))
                    .role(jwtUtils.extractRole(claims))
                    .build();
        }

        // 5. Générer de nouveaux tokens
        return generateTokens(user);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
        log.info("User logged out");
    }

    // ─────────────────────────────────────────────────────
    // PRIVATE METHODS
    // ─────────────────────────────────────────────────────

    private AuthResponse generateTokens(UserDto user) {
        // Generate access token (includes agenceId for downstream RBAC)
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), user.getAgenceId());

        // Generate refresh token
        String refreshTokenStr = jwtUtils.generateRefreshToken(user.getId(), user.getEmail(), user.getRole(), user.getAgenceId());

        // Persist refresh token in DB
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenStr)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationMs() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getAccessExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nom(user.getNom())
                        .prenom(user.getPrenom())
                        .role(user.getRole())
                        .agenceId(user.getAgenceId())
                        .build())
                .build();
    }
}
