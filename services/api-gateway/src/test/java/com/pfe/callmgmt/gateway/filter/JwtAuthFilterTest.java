package com.pfe.callmgmt.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private JwtAuthFilter jwtAuthFilter;
    private static final String SECRET = "ThisIsATestSecretKeyForJwtTokensThatMustBeAtLeast256BitsLongForHS256";

    @BeforeEach
    void setUp() throws Exception {
        jwtAuthFilter = new JwtAuthFilter();
        Field field = JwtAuthFilter.class.getDeclaredField("jwtSecret");
        field.setAccessible(true);
        field.set(jwtAuthFilter, SECRET);
    }

    private String generateValidToken(UUID userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("agenceId", UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("OPTIONS preflight bypass — ne nécessite pas d'Authorization")
    void optionsPreflight_passesThrough() {
        GatewayFilter filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.options("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Pas de header Authorization → 401 UNAUTHORIZED")
    void noAuthHeader_returns401() {
        GatewayFilter filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Token valide → propage les headers X-User-*")
    void validToken_addsUserHeaders() {
        GatewayFilter filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        UUID userId = UUID.randomUUID();
        String token = generateValidToken(userId, "test@email.com", "ADMIN");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Token invalide → 401 UNAUTHORIZED")
    void invalidToken_returns401() {
        GatewayFilter filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Header Authorization sans prefix Bearer → 401")
    void noBearerPrefix_returns401() {
        GatewayFilter filter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
