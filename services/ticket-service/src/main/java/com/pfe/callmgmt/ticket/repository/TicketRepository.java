package com.pfe.callmgmt.ticket.repository;

import com.pfe.callmgmt.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

        @Query(value = "SELECT * FROM tickets t WHERE " +
                        "(:statut IS NULL OR t.statut = CAST(:statut AS VARCHAR)) AND " +
                        "(:canal IS NULL OR t.canal = CAST(:canal AS VARCHAR)) AND " +
                        "(CAST(:agenceId AS UUID) IS NULL OR t.agence_id = CAST(:agenceId AS UUID)) AND " +
                        "(CAST(:operateurId AS UUID) IS NULL OR t.operateur_id = CAST(:operateurId AS UUID)) AND " +
                        "(CAST(:dateFrom AS TIMESTAMP) IS NULL OR t.created_at >= CAST(:dateFrom AS TIMESTAMP)) AND " +
                        "(CAST(:dateTo AS TIMESTAMP) IS NULL OR t.created_at <= CAST(:dateTo AS TIMESTAMP)) AND " +
                        "(CAST(:search AS VARCHAR) IS NULL OR " +
                        "  LOWER(t.client_nom) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) OR " +
                        "  LOWER(t.client_tel) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) OR " +
                        "  LOWER(t.numero) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')))" +
                        " ORDER BY t.created_at DESC", countQuery = "SELECT count(*) FROM tickets t WHERE " +
                                        "(:statut IS NULL OR t.statut = CAST(:statut AS VARCHAR)) AND " +
                                        "(:canal IS NULL OR t.canal = CAST(:canal AS VARCHAR)) AND " +
                                        "(CAST(:agenceId AS UUID) IS NULL OR t.agence_id = CAST(:agenceId AS UUID)) AND "
                                        +
                                        "(CAST(:operateurId AS UUID) IS NULL OR t.operateur_id = CAST(:operateurId AS UUID)) AND "
                                        +
                                        "(CAST(:dateFrom AS TIMESTAMP) IS NULL OR t.created_at >= CAST(:dateFrom AS TIMESTAMP)) AND "
                                        +
                                        "(CAST(:dateTo AS TIMESTAMP) IS NULL OR t.created_at <= CAST(:dateTo AS TIMESTAMP)) AND "
                                        +
                                        "(CAST(:search AS VARCHAR) IS NULL OR " +
                                        "  LOWER(t.client_nom) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) OR "
                                        +
                                        "  LOWER(t.client_tel) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) OR "
                                        +
                                        "  LOWER(t.numero) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')))", nativeQuery = true)
        Page<Ticket> findByFilters(
                        @Param("statut") String statut,
                        @Param("canal") String canal,
                        @Param("agenceId") UUID agenceId,
                        @Param("operateurId") UUID operateurId,
                        @Param("dateFrom") LocalDateTime dateFrom,
                        @Param("dateTo") LocalDateTime dateTo,
                        @Param("search") String search,
                        Pageable pageable);

        long countByStatutAndAgenceId(Ticket.Statut statut, UUID agenceId);

        long countByStatut(Ticket.Statut statut);

        List<Ticket> findTop5ByStatutAndAgenceIdOrderByCreatedAtAsc(Ticket.Statut statut, UUID agenceId);

        // ========== File d'attente (Queue) ==========

        /**
         * Tickets en file d'attente : NOUVEAU, non assignés, ordonnés par priorité puis
         * date
         */
        List<Ticket> findByStatutAndOperateurIdIsNullOrderByPrioriteAscCreatedAtAsc(Ticket.Statut statut);

        /** File d'attente filtrée par agence */
        List<Ticket> findByStatutAndOperateurIdIsNullAndAgenceIdOrderByPrioriteAscCreatedAtAsc(
                        Ticket.Statut statut, UUID agenceId);

        /** Nombre de tickets en file d'attente */
        long countByStatutAndOperateurIdIsNull(Ticket.Statut statut);

        /** Nombre de tickets en file d'attente par agence */
        long countByStatutAndOperateurIdIsNullAndAgenceId(Ticket.Statut statut, UUID agenceId);
}
