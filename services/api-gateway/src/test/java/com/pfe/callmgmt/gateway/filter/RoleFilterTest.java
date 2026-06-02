package com.pfe.callmgmt.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleFilterTest {

    private RoleFilter roleFilter;

    @BeforeEach
    void setUp() {
        roleFilter = new RoleFilter();
    }

    @Test
    @DisplayName("OPTIONS preflight → passe sans vérification de rôle")
    void optionsPreflight_passesThrough() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles("ADMIN,SUPERVISOR");

        GatewayFilter filter = roleFilter.apply(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.options("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Pas de header X-User-Role → 403 FORBIDDEN")
    void noRoleHeader_returns403() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles("ADMIN");

        GatewayFilter filter = roleFilter.apply(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Rôle autorisé → passe au filtre suivant")
    void allowedRole_passesThrough() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles("ADMIN,SUPERVISOR");

        GatewayFilter filter = roleFilter.apply(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-User-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Rôle non autorisé → 403 FORBIDDEN")
    void unauthorizedRole_returns403() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles("ADMIN,SUPERVISOR");

        GatewayFilter filter = roleFilter.apply(config);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-User-Role", "OPERATEUR")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
