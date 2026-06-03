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

    // --- SonarCloud: extracted string constants ---
    private static final String EMAIL_TEST = "test@company.com";
    private static final String USER_NAME = "Test User";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String NEW_PASS = "NewPass456";

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
        validRequest.setEmail(EMAIL_TEST);
        validRequest.setPassword("SecurePass123");
        validRequest.setNom(USER_NAME); /* prenom set below */
        validRequest.setTelephone("12345678");
        validRequest.setRole(ROLE_OPERATOR);
        validRequest.setAgenceId(agenceId);

        savedUser = User.builder()
                .id(userId)
                .email(EMAIL_TEST)
                .passwordHash("$2a$12$encoded")
                .nom(USER_NAME).prenom("")
                .telephone("12345678")
                .role(ROLE_OPERATOR)
                .agence(agence)
                .actif(true)
                .build();

        userResponse = UserResponse.builder()
                .id(userId)
                .email(EMAIL_TEST)
                .nom(USER_NAME).prenom("")
                .role(ROLE_OPERATOR)
                .agenceId(agenceId)
                .agenceName("Agence Test")
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("1. createUser — succès")
    void createUser_success() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$encoded");
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        UserResponse result = userService.createUser(validRequest, null);

        assertNotNull(result);
        assertEquals(EMAIL_TEST, result.getEmail());
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishUserCreated(any());
    }

    @Test
    @DisplayName("2. createUser — email déjà existant → ConflictException")
    void createUser_emailExists_throwsConflict() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(true);

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

        User otherUser = User.builder().id(otherUserId).role(ROLE_OPERATOR).agence(agence).build();
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class,
                () -> userService.updateUser(otherUserId, validRequest, operatorId, ROLE_OPERATOR, agenceId));
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
        when(userRepository.findByActifTrueAndRole(eq(ROLE_OPERATOR), any(Pageable.class))).thenReturn(userPage);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(ROLE_OPERATOR, null, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(ROLE_OPERATOR, result.getContent().get(0).getRole());
    }

    @Test
    @DisplayName("9. changePassword — succès")
    void changePassword_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("OldPass123", savedUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASS)).thenReturn("$2a$12$newEncoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.changePassword(userId, "OldPass123", NEW_PASS);

        verify(passwordEncoder).encode(NEW_PASS);
        verify(userRepository).save(savedUser);
    }

    @Test
    @DisplayName("10. changePassword — ancien mot de passe incorrect → IllegalArgumentException")
    void changePassword_wrongOldPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("WrongPass", savedUser.getPasswordHash())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword(userId, "WrongPass", NEW_PASS));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("11. getUserById — succès")
    void getUserById_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        UserResponse result = userService.getUserById(userId);

        assertNotNull(result);
        assertEquals(EMAIL_TEST, result.getEmail());
    }

    @Test
    @DisplayName("12. getUserByEmail — utilisateur trouvé")
    void getUserByEmail_found() {
        when(userRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(savedUser));
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        UserResponse result = userService.getUserByEmail(EMAIL_TEST);

        assertNotNull(result);
        assertEquals(EMAIL_TEST, result.getEmail());
    }

    @Test
    @DisplayName("13. getUserByEmail — non trouvé → EntityNotFoundException")
    void getUserByEmail_notFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                userService.getUserByEmail("unknown@test.com"));
    }

    @Test
    @DisplayName("14. getAllUsers — sans filtres retourne tous les actifs")
    void getAllUsers_noFilter_returnsAll() {
        Page<User> page = new PageImpl<>(List.of(savedUser));
        when(userRepository.findByActifTrue(any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(null, null, 0, 20);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("15. getAllUsers — filtre par agenceId uniquement")
    void getAllUsers_filterByAgence_returnsFiltered() {
        Page<User> page = new PageImpl<>(List.of(savedUser));
        when(userRepository.findByActifTrueAndAgenceId(eq(agenceId), any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(null, agenceId, 0, 20);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("16. getAllUsers — filtre par rôle ET agenceId")
    void getAllUsers_filterByRoleAndAgence_returnsFiltered() {
        Page<User> page = new PageImpl<>(List.of(savedUser));
        when(userRepository.findByActifTrueAndAgenceIdAndRole(eq(agenceId), eq(ROLE_OPERATOR), any(Pageable.class)))
                .thenReturn(page);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getAllUsers(ROLE_OPERATOR, agenceId, 0, 20);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("17. updateLastLogin — met à jour la date de dernière connexion")
    void updateLastLogin_updatesField() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.updateLastLogin(userId);

        verify(userRepository).save(savedUser);
        assertNotNull(savedUser.getLastLogin());
    }

    @Test
    @DisplayName("18. updateUser — OPERATOR tente de changer son rôle → AccessDeniedException")
    void updateUser_operatorCantChangeRole() {
        UserRequest req = new UserRequest();
        req.setNom("Test"); req.setEmail(EMAIL_TEST);
        req.setRole("SUPERVISOR"); // tentative de changement de rôle
        req.setAgenceId(agenceId);

        // Même userId → l'opérateur modifie son propre profil mais tente un role change
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));

        assertThrows(AccessDeniedException.class,
                () -> userService.updateUser(userId, req, userId, ROLE_OPERATOR, agenceId));
    }

    @Test
    @DisplayName("19. updateUser — SUPERVISOR tente de modifier utilisateur d'une autre agence → AccessDeniedException")
    void updateUser_supervisorCantUpdateOtherAgence() {
        UUID otherAgenceId = UUID.randomUUID();
        UUID supervisorId = UUID.randomUUID();
        Agence otherAgence = Agence.builder().id(otherAgenceId).nom("Autre Agence").build();
        User otherUser = User.builder().id(userId).role(ROLE_OPERATOR).agence(otherAgence).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class,
                () -> userService.updateUser(userId, validRequest, supervisorId, "SUPERVISOR", agenceId));
    }

    @Test
    @DisplayName("20. createUserInternal — succès avec mot de passe déjà hashé")
    void createUserInternal_success() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(false);
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        var result = userService.createUserInternal(validRequest);

        assertNotNull(result);
        assertEquals(EMAIL_TEST, result.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("21. createUser — OPERATOR sans agenceId → IllegalArgumentException")
    void createUser_operatorWithoutAgence_throws() {
        UserRequest req = new UserRequest();
        req.setEmail("op@test.com");
        req.setPassword("Pass123");
        req.setNom("Op"); req.setRole(ROLE_OPERATOR);
        req.setAgenceId(null); // manquant

        when(userRepository.existsByEmail("op@test.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.createUser(req, null));
    }

    @Test
    @DisplayName("22. createUser — SUPERVISOR sans agenceId → IllegalArgumentException")
    void createUser_supervisorWithoutAgence_throws() {
        UserRequest req = new UserRequest();
        req.setEmail("sup@test.com");
        req.setPassword("Pass123");
        req.setNom("Sup"); req.setRole("SUPERVISOR");
        req.setAgenceId(null);

        when(userRepository.existsByEmail("sup@test.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.createUser(req, null));
    }

    @Test
    @DisplayName("23. createUserInternal — OPERATOR sans agenceId → IllegalArgumentException")
    void createUserInternal_operatorWithoutAgence_throws() {
        UserRequest req = new UserRequest();
        req.setEmail("op-internal@test.com");
        req.setPassword("Pass123");
        req.setNom("Op"); req.setRole(ROLE_OPERATOR);
        req.setAgenceId(null);

        when(userRepository.existsByEmail("op-internal@test.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.createUserInternal(req));
    }

    @Test
    @DisplayName("24. createUserInternal — email déjà existant → ConflictException")
    void createUserInternal_emailExists_throwsConflict() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(true);
        assertThrows(UserServiceImpl.ConflictException.class, () ->
                userService.createUserInternal(validRequest));
    }

    @Test
    @DisplayName("25. createUser — ADMIN sans agenceId → Succès")
    void createUser_adminWithoutAgence_success() {
        UserRequest req = new UserRequest();
        req.setEmail("admin@test.com");
        req.setPassword("Pass123");
        req.setNom("Admin"); req.setRole("ADMIN");
        req.setAgenceId(null);

        User adminUser = User.builder().id(UUID.randomUUID()).email("admin@test.com").role("ADMIN").build();
        UserResponse adminResp = UserResponse.builder().email("admin@test.com").role("ADMIN").build();

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass123")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenReturn(adminUser);
        when(userMapper.toResponse(adminUser)).thenReturn(adminResp);

        UserResponse result = userService.createUser(req, null);
        assertNotNull(result);
        assertEquals("admin@test.com", result.getEmail());
    }

    @Test
    @DisplayName("26. createUser — exception de publication d'événement ignorée")
    void createUser_eventPublisherThrows_loggedAndIgnored() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$12$encoded");
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);
        doThrow(new RuntimeException("RabbitMQ connection error")).when(eventPublisher).publishUserCreated(any());

        UserResponse result = userService.createUser(validRequest, null);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishUserCreated(any());
    }

    @Test
    @DisplayName("27. createUserInternal — exception de publication d'événement ignorée")
    void createUserInternal_eventPublisherThrows_loggedAndIgnored() {
        when(userRepository.existsByEmail(EMAIL_TEST)).thenReturn(false);
        when(agenceRepository.findById(agenceId)).thenReturn(Optional.of(agence));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doThrow(new RuntimeException("RabbitMQ connection error")).when(eventPublisher).publishUserCreated(any());

        var result = userService.createUserInternal(validRequest);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("28. updateUser — mot de passe et rôle null/vide → pas de changement de mot de passe")
    void updateUser_fieldsNullOrBlank_noException() {
        UserRequest req = new UserRequest();
        req.setNom("Updated");
        req.setRole(null);
        req.setPassword("");
        req.setAgenceId(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        UserResponse result = userService.updateUser(userId, req, UUID.randomUUID(), "ADMIN", null);
        assertNotNull(result);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("29. updateUser — SUPERVISOR modifie un utilisateur sans agence → succès")
    void updateUser_supervisorModifiesUserWithoutAgence_success() {
        UUID supervisorId = UUID.randomUUID();
        User target = User.builder().id(userId).role(ROLE_OPERATOR).agence(null).build();

        UserRequest req = new UserRequest();
        req.setNom("Updated");
        req.setRole(ROLE_OPERATOR);
        req.setAgenceId(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        UserResponse result = userService.updateUser(userId, req, supervisorId, "SUPERVISOR", agenceId);
        assertNotNull(result);
    }

    @Test
    @DisplayName("30. getUserByEmailInternal — trouvé → retourne InternalUserResponse")
    void getUserByEmailInternal_found() {
        when(userRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(savedUser));
        var result = userService.getUserByEmailInternal(EMAIL_TEST);
        assertNotNull(result);
        assertEquals(EMAIL_TEST, result.getEmail());
    }

    @Test
    @DisplayName("31. getUserByEmailInternal — non trouvé → EntityNotFoundException")
    void getUserByEmailInternal_notFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () ->
                userService.getUserByEmailInternal("unknown@test.com"));
    }
}
