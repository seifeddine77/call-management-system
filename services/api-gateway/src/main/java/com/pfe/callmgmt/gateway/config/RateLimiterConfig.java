package com.pfe.callmgmt.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configuration du Rate Limiting par IP.
 * <p>
 * Utilise le RequestRateLimiter intégré de Spring Cloud Gateway avec Redis.
 * Chaque IP est limitée à 100 requêtes/minute (configurable dans
 * application.yml).
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Résoud la clé de rate limiting par adresse IP du client.
     * Utilise X-Forwarded-For si présent (derrière un proxy/load balancer),
     * sinon l'adresse IP directe.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            } else {
                // X-Forwarded-For peut contenir plusieurs IPs, prendre la première
                ip = ip.split(",")[0].trim();
            }
            return Mono.just(ip);
        };
    }
}
