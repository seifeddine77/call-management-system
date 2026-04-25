package com.pfe.callmgmt.user.service;

import com.pfe.callmgmt.user.dto.UserRequest;
import com.pfe.callmgmt.user.dto.UserResponse;
import com.pfe.callmgmt.user.entity.Agence;
import com.pfe.callmgmt.user.entity.User;
import com.pfe.callmgmt.user.event.UserEventPublisher;
import com.pfe.callmgmt.user.mapper.UserMapper;
import com.pfe.callmgmt.user.repository.AgenceRepository;
import com.pfe.callmgmt.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AgenceRepository agenceRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    private UserRequest validRequest;
    private User savedUser;
    private UserResponse userResponse;
    private Agence agence;
    private UUID userId;
    private UUID agenceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        agenceId = UUID.randomUUID();

        agence = Agence.builder().id(agenceId).nom("Agence Test").build();

        validRequest = new UserRequest();
        validRequest.setEmail("test@company.com");
        validRequest.setPassword("SecurePass123");
        validRequest.setNom("Test User"); /* prenom set below */;
        validRequest.setTelephone("12345678");
        validRequest.setRole("OPERATOR");
        validRequest.setAgenceId(agenceId);

        savedUser = User.builder()
                .id(userId)
                .email("test@company.com")
                .passwordHash("$2a$12$encoded")
                .nom("Test User").prenom("")
                .telephone("12345678")
                .role("OPERATOR")
                .agence(agence)
                .actif(true)
                .build();

        userResponse = UserResponse.builder()
                .id(userId)
                .email("test@company.com")
                .nom("Test User").prenom("")
                .role("OPERATOR")
                .agenceId(agenceId)
                .agenceName("Agence Test")
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("1. createUser — succès")
    void createUser_success() {
        when(userRepository.existsByEmail("test@company.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$encoded");
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        UserResponse result = userService.createUser(validRequest, null);

        assertNotNull(result);
        assertEquals("test@company.com", result.getEmail());
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishUserCreated(any());
    }

    @Test
    @DisplayName("2. createUser — email déjà existant → ConflictException")
    void createUser_emailExists_throwsConflict() {
        when(userRepository.existsByEmail("test@company.com")).thenReturn(true);

        assertThrows(UserServiceImpl.ConflictException.class, () ->
                userService.createUser(validRequest, null));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. updateUser — ADMIN peut modifier tout utilisateur")
    void updateUser_adminCanUpdateAny() {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        UserResponse result = userService.updateUser(userId, validRequest, adminId, "ADMIN", null);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("4. updateUser — OPERATOR ne peut modifier que lui-même → AccessDeniedException")
    void updateUser_operatorCanOnlyUpdateSelf() {
        UUID otherUserId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        User otherUser = User.builder().id(otherUserId).role("OPERATOR").agence(agence).build();
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class,
                () -> userService.updateUser(otherUserId, validRequest, operatorId, "OPERATOR", agenceId));
    }

    @Test
    @DisplayName("5. updateUser — SUPERVISOR peut modifier les utilisateurs de son agence")
    void updateUser_supervisorCanUpdateOwnAgence() {
        UUID supervisorId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        UserResponse result = userService.updateUser(userId, validRequest, supervisorId, "SUPERVISOR", agenceId);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("6. deactivateUser — met actif à false")
    void deactivateUser_setsActifFalse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.deactivateUser(userId);

        assertFalse(savedUser.isActif());
        verify(userRepository).save(savedUser);
    }

    @Test
    @DisplayName("7. getUserById — utilisateur non trouvé → EntityNotFoundException")
    void getUserById_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.getUserById(unknownId));
    }

    @Test
    @DisplayName("8. getAllUsers — filtre par rôle")
    void getAllUsers_filterByRole() {
        Page<User> userPage = new PageImpl<>(List.of(savedUser));
        when(userRepository.findByActifTrueAndRole(eq("OPERATOR"), any(Pageable.class))).thenReturn(userPage);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers("OPERATOR", null, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals("OPERATOR", result.getContent().get(0).getRole());
    }

    @Test
    @DisplayName("9. changePassword — succès")
    void changePassword_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("OldPass123", savedUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("$2a$12$newEncoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.changePassword(userId, "OldPass123", "NewPass456");

        verify(passwordEncoder).encode("NewPass456");
        verify(userRepository).save(savedUser);
    }

    @Test
    @DisplayName("10. changePassword — ancien mot de passe incorrect → IllegalArgumentException")
    void changePassword_wrongOldPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("WrongPass", savedUser.getPasswordHash())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword(userId, "WrongPass", "NewPass456"));
        verify(userRepository, never()).save(any());
    }
}
