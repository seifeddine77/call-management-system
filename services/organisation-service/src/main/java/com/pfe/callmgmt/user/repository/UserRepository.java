package com.pfe.callmgmt.user.repository;

import com.pfe.callmgmt.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    Page<User> findByAgenceId(UUID agenceId, Pageable pageable);

    Page<User> findByRole(String role, Pageable pageable);

    List<User> findByAgenceIdAndRole(UUID agenceId, String role);

    boolean existsByEmail(String email);

    Page<User> findByAgenceIdAndRole(UUID agenceId, String role, Pageable pageable);

    // Filtered by actif=true (default view — hide deactivated users)
    Page<User> findByActifTrue(Pageable pageable);

    Page<User> findByActifTrueAndRole(String role, Pageable pageable);

    Page<User> findByActifTrueAndAgenceId(UUID agenceId, Pageable pageable);

    Page<User> findByActifTrueAndAgenceIdAndRole(UUID agenceId, String role, Pageable pageable);
}
