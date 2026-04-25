package com.pfe.callmgmt.rules.repository;

import com.pfe.callmgmt.rules.entity.Rule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    /**
     * Trouver toutes les règles actives triées par priorité décroissante
     */
    List<Rule> findByActifTrueOrderByPrioriteDesc();

    /**
     * Lister les règles avec filtre actif/tous, paginé
     */
    Page<Rule> findByActif(Boolean actif, Pageable pageable);

    /**
     * Compter les règles actives
     */
    long countByActifTrue();
}
