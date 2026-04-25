package com.pfe.callmgmt.user.contract;

import com.pfe.callmgmt.user.controller.UserController;
import com.pfe.callmgmt.user.dto.InternalUserResponse;
import com.pfe.callmgmt.user.service.UserService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base class for Spring Cloud Contract generated tests (Producer side).
 *
 * Security is fully disabled so generated tests can call endpoints freely.
 *
 * NOTE: We deliberately use "testoperator" (no @ sign) as email in the
 * GET /email/{email} contract because Feign URL-encodes @ as %40, which
 * breaks WireMock path matching. This contract still validates the correct
 * HTTP response shape — which is the point.
 */
@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
})
@ActiveProfiles("test")
public abstract class ContractBaseTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private UserService userService;

        @BeforeEach
        void setup() {
                RestAssuredMockMvc.mockMvc(mockMvc);

                // Stub: GET /api/users/email/testoperator
                // Note: Using a simple identifier without @ to avoid Feign URL-encoding issues
                InternalUserResponse existingUser = InternalUserResponse.builder()
                                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                                .email("testoperator")
                                .passwordHash("$2a$10$hashedpassword")
                                .nom("Opérateur Test").prenom("")
                                .telephone("98111222")
                                .role("OPERATOR")
                                .actif(true)
                                .build();

                when(userService.getUserByEmailInternal(eq("testoperator")))
                                .thenReturn(existingUser);

                // Stub: POST /api/users/internal
                InternalUserResponse createdUser = InternalUserResponse.builder()
                                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
                                .email("newuser@test.com")
                                .passwordHash("$2a$10$hashedpassword")
                                .nom("Nouveau Utilisateur").prenom("")
                                .telephone("98222333")
                                .role("OPERATOR")
                                .actif(true)
                                .build();

                when(userService.createUserInternal(any()))
                                .thenReturn(createdUser);
        }
}
