package com.pfe.callmgmt.auth.client;

import com.pfe.callmgmt.auth.dto.RegisterRequest;
import com.pfe.callmgmt.auth.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "organisation-service", url = "${USER_SERVICE_URL:}", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/api/users/email/{email}")
    ResponseEntity<UserDto> findByEmail(@PathVariable("email") String email);

    @PostMapping("/api/users/internal")
    ResponseEntity<UserDto> createUser(@RequestBody RegisterRequest request);

    @PatchMapping("/api/users/{id}/last-login")
    ResponseEntity<Void> updateLastLogin(@PathVariable("id") java.util.UUID id);
}
