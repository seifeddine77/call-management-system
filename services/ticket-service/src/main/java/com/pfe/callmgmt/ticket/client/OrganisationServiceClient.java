package com.pfe.callmgmt.ticket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

/**
 * Feign client vers l'organisation-service.
 * Utilisé pour résoudre les noms d'agences et d'opérateurs à partir de leurs UUIDs.
 */
@FeignClient(name = "organisation-service")
public interface OrganisationServiceClient {

    @GetMapping("/api/agences/{id}")
    Map<String, Object> getAgenceById(@PathVariable("id") UUID id);

    @GetMapping("/api/users/{id}")
    Map<String, Object> getUserById(@PathVariable("id") UUID id);
}
