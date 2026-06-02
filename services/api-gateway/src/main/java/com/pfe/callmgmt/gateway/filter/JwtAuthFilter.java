package com.pfe.callmgmt.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@SuppressWarnings("java:S5527") // JWT secret injected from environment variable via @Value — not hardcoded
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthFilter() {
        super(Config.class);
    }

    @SuppressWarnings("java:S2094") // Required by AbstractGatewayFilterFactory — must exist even if empty
    public static class Config {
        // Configuration properties can be added here if needed
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // ── CORS preflight bypass ──
            // Browsers send OPTIONS requests without Authorization headers.
            // These must pass through so the Gateway's globalcors config can
            // respond with the correct Access-Control-Allow-* headers.
            if (request.getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            // Check for Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Aucun token d'authentification fourni", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Format de token invalide", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Jws<Claims> claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token);

                Claims body = claims.getPayload();
                String userId = body.getSubject();
                String email = body.get("email", String.class);
                String role = body.get("role", String.class);

                // Add user info as headers for downstream services
                String agenceId = body.get("agenceId", String.class);
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Email", email != null ? email : "")
                        .header("X-User-Role", role != null ? role : "")
                        .header("X-User-Agence-Id", agenceId != null ? agenceId : "")
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
                log.error("JWT validation failed: type={}, message={}, secretLength={}, tokenLength={}",
                        e.getClass().getSimpleName(), e.getMessage(), jwtSecret.length(), token.length());
                return onError(exchange, "Token invalide ou expiré", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"error\":\"%s\",\"status\":%d}", message, status.value());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
