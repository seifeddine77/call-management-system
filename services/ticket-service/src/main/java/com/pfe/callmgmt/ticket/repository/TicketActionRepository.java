package com.pfe.callmgmt.ticket.repository;

import com.pfe.callmgmt.ticket.entity.TicketAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketActionRepository extends JpaRepository<TicketAction, UUID> {

    List<TicketAction> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
