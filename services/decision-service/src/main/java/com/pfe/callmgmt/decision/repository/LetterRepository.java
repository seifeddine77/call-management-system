package com.pfe.callmgmt.decision.repository;

import com.pfe.callmgmt.decision.entity.Letter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LetterRepository extends JpaRepository<Letter, UUID> {

    List<Letter> findByDecisionId(UUID decisionId);
}
