package com.pfe.callmgmt.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Security filter chain for the Ticket microservice.
     * <p>
     * CSRF is disabled because this is a stateless REST API that uses JWT bearer tokens
     * for authentication (no cookies or server-side sessions). CSRF attacks exploit
     * cookie-based authentication which is not used here.
     * <p>
     * All endpoints are permitted because this service runs behind the API Gateway
     * which handles JWT validation and RBAC via {@code JwtAuthFilter} and {@code RoleFilter}.
     * Direct access to this service is blocked at the network level (Docker internal network).
     */
    @SuppressWarnings("java:S4502") // CSRF disabled intentionally — stateless JWT API behind API Gateway
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/tickets/**").permitAll()
                        .requestMatchers("/api/channels/**").permitAll() // Canaux publics (VoIP/Email/Web)
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().permitAll()); // Security enforced at Gateway level (JwtAuthFilter + RoleFilter)
        return http.build();
    }
}
