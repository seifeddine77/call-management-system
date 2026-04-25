package com.pfe.callmgmt.rules.repository;

import com.pfe.callmgmt.rules.entity.ReferenceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les éléments de référence (CDC 4.B — Paramétrage)
 */
@Repository
public interface ReferenceItemRepository extends JpaRepository<ReferenceItem, UUID> {

    List<ReferenceItem> findByCategoryAndActifTrueOrderByDisplayOrderAsc(String category);

    List<ReferenceItem> findByCategoryOrderByDisplayOrderAsc(String category);

    Optional<ReferenceItem> findByCategoryAndCode(String category, String code);

    boolean existsByCategoryAndCode(String category, String code);
}
