package com.pfe.callmgmt.user.repository;

import com.pfe.callmgmt.user.entity.Agence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgenceRepository extends JpaRepository<Agence, UUID> {
    Optional<Agence> findByNom(String nom);

    Page<Agence> findByActifTrue(Pageable pageable);
}
