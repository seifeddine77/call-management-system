package com.pfe.callmgmt.rules.repository;

import com.pfe.callmgmt.rules.entity.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {

    /**
     * Historique des versions d'une règle, du plus récent au plus ancien
     */
    List<RuleVersion> findByRuleIdOrderByVersionDesc(UUID ruleId);
}
