package com.pfe.callmgmt.decision.repository;

import com.pfe.callmgmt.decision.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByTicketId(UUID ticketId);

    List<Decision> findByDecisionAndCreatedAtBetween(String decision, LocalDateTime from, LocalDateTime to);

    long countByDecision(String decision);

    long countByFinalDecision(String finalDecision);
}
