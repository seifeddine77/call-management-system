package com.pfe.callmgmt.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class RoleFilter extends AbstractGatewayFilterFactory<RoleFilter.Config> {

    public RoleFilter() {
        super(Config.class);
    }

    @Getter
    @Setter
    public static class Config {
        private String roles; // Comma-separated roles: "SUPERVISOR,ADMIN"
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // ── CORS preflight bypass ──
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");

            if (userRole == null || userRole.isEmpty()) {
                return onError(exchange, "Rôle utilisateur non trouvé");
            }

            List<String> allowedRoles = Arrays.asList(config.getRoles().split(","));

            if (!allowedRoles.contains(userRole.trim())) {
                log.warn("Access denied: user role {} not in allowed roles {}", userRole, allowedRoles);
                return onError(exchange, "Accès refusé — rôle insuffisant");
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"error\":\"%s\",\"status\":403}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}

