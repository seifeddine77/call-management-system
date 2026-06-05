package com.pfe.callmgmt.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtils {

    private static final String CLAIM_EMAIL = "email";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.refresh-secret}")
    private String jwtRefreshSecret;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Génère un access token JWT (durée: 15 minutes)
     */
    public String generateAccessToken(UUID userId, String email, String role, UUID agenceId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim("role", role)
                .claim("agenceId", agenceId != null ? agenceId.toString() : null)
                .claim("type", "ACCESS")
                .issuedAt(java.util.Date.from(java.time.Instant.now()))
                .expiration(java.util.Date.from(java.time.Instant.now().plusMillis(accessExpirationMs)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Génère un refresh token JWT (durée: 7 jours)
     */
    public String generateRefreshToken(UUID userId, String email, String role, UUID agenceId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtRefreshSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim("role", role)
                .claim("agenceId", agenceId != null ? agenceId.toString() : null)
                .claim("type", "REFRESH")
                .issuedAt(java.util.Date.from(java.time.Instant.now()))
                .expiration(java.util.Date.from(java.time.Instant.now().plusMillis(refreshExpirationMs)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valide un access token et retourne les claims
     */
    public Jws<Claims> validateAccessToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    /**
     * Valide un refresh token et retourne les claims
     */
    public Jws<Claims> validateRefreshToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtRefreshSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public UUID extractUserId(Jws<Claims> claims) {
        return UUID.fromString(claims.getPayload().getSubject());
    }

    public String extractEmail(Jws<Claims> claims) {
        return claims.getPayload().get(CLAIM_EMAIL, String.class);
    }

    public String extractRole(Jws<Claims> claims) {
        return claims.getPayload().get("role", String.class);
    }

    public LocalDateTime extractExpiry(Jws<Claims> claims) {
        Date expiration = claims.getPayload().getExpiration();
        return expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public boolean isTokenExpired(String token) {
        try {
            validateAccessToken(token);
            return false;
        } catch (JwtException e) {
            return true;
        }
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }
}
