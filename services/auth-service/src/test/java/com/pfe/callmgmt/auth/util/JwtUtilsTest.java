package com.pfe.callmgmt.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    // 64+ chars to satisfy HS256 minimum key length
    private static final String TEST_SECRET = "ThisIsATestSecretKeyForJwtTokensThatMustBeAtLeast256BitsLongForHS256";
    private static final String TEST_REFRESH_SECRET = "ThisIsARefreshSecretKeyForJwtTokensThatMustBeAtLeast256BitsForHS256";

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtils();
        setField(jwtUtils, "jwtSecret", TEST_SECRET);
        setField(jwtUtils, "jwtRefreshSecret", TEST_REFRESH_SECRET);
        setField(jwtUtils, "accessExpirationMs", 900_000L); // 15 min
        setField(jwtUtils, "refreshExpirationMs", 604_800_000L); // 7 days
    }

    @Test
    @DisplayName("Génère et valide un access token avec claims corrects")
    void generateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID agenceId = UUID.randomUUID();

        String token = jwtUtils.generateAccessToken(userId, "test@email.com", "OPERATEUR", agenceId);

        assertThat(token).isNotBlank();

        Jws<Claims> claims = jwtUtils.validateAccessToken(token);

        assertThat(jwtUtils.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtUtils.extractEmail(claims)).isEqualTo("test@email.com");
        assertThat(jwtUtils.extractRole(claims)).isEqualTo("OPERATEUR");
        assertThat(claims.getPayload().get("agenceId", String.class)).isEqualTo(agenceId.toString());
        assertThat(claims.getPayload().get("type", String.class)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("Génère et valide un refresh token")
    void generateAndValidateRefreshToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtUtils.generateRefreshToken(userId, "user@test.com", "ADMIN", null);

        assertThat(token).isNotBlank();

        Jws<Claims> claims = jwtUtils.validateRefreshToken(token);

        assertThat(jwtUtils.extractUserId(claims)).isEqualTo(userId);
        assertThat(claims.getPayload().get("type", String.class)).isEqualTo("REFRESH");
        assertThat(claims.getPayload().get("agenceId")).isNull();
    }

    @Test
    @DisplayName("validateAccessToken rejette un token invalide")
    void validateAccessToken_invalid_throws() {
        assertThatThrownBy(() -> jwtUtils.validateAccessToken("invalid.token.here"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("isTokenExpired retourne false pour un token valide")
    void isTokenExpired_validToken_returnsFalse() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateAccessToken(userId, "test@email.com", "ADMIN", null);

        assertThat(jwtUtils.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired retourne true pour un token invalide")
    void isTokenExpired_invalidToken_returnsTrue() {
        assertThat(jwtUtils.isTokenExpired("garbage")).isTrue();
    }

    @Test
    @DisplayName("extractExpiry retourne une date future pour un token fraîchement généré")
    void extractExpiry_returnsFutureDate() {
        String token = jwtUtils.generateAccessToken(UUID.randomUUID(), "test@email.com", "ADMIN", null);
        Jws<Claims> claims = jwtUtils.validateAccessToken(token);

        LocalDateTime expiry = jwtUtils.extractExpiry(claims);
        LocalDateTime benchmark = LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 0, 0);

        assertThat(expiry).isAfter(benchmark);
    }

    @Test
    @DisplayName("getAccessExpirationMs et getRefreshExpirationMs retournent les bonnes valeurs")
    void expirationGetters() {
        assertThat(jwtUtils.getAccessExpirationMs()).isEqualTo(900_000L);
        assertThat(jwtUtils.getRefreshExpirationMs()).isEqualTo(604_800_000L);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
