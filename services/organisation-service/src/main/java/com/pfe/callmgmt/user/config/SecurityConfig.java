package com.pfe.callmgmt.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Security filter chain for the Organisation microservice.
     * <p>
     * CSRF is disabled because this is a stateless REST API that uses JWT bearer tokens
     * (no cookies or server-side sessions). CSRF protection is unnecessary.
     * <p>
     * All endpoints permit unauthenticated access because this service runs behind
     * the API Gateway which handles JWT validation via {@code JwtAuthFilter}.
     * Direct access is blocked at the Docker network level.
     */
    @SuppressWarnings("java:S4502") // CSRF disabled intentionally — stateless JWT API behind API Gateway
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Internal endpoints (called by auth-service via Feign)
                        .requestMatchers("/api/users/internal").permitAll()
                        .requestMatchers("/api/users/email/**").permitAll()
                        // Actuator & Swagger
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        // All other endpoints require the Gateway to have set X-User headers
                        .anyRequest().permitAll()); // Security enforced at Gateway level (JwtAuthFilter + RoleFilter)

        return http.build();
    }
}
