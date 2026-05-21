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
                        "(CAST(:priorite AS INTEGER) IS NULL OR t.priorite = CAST(:priorite AS SMALLINT)) AND " +
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
                                        "(CAST(:priorite AS INTEGER) IS NULL OR t.priorite = CAST(:priorite AS SMALLINT)) AND " +
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
                        @Param("priorite") Short priorite,
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

        /** Performance par opérateur — agrégation GROUP BY operateur_id.
         *  tempsMoyenMinutes = temps entre ASSIGNE et RESOLU (via ticket_actions),
         *  pas le lifetime complet du ticket. */
        @Query(value = "SELECT t.operateur_id AS operateurId, " +
                        "COUNT(*) AS ticketsTraites, " +
                        "COUNT(*) FILTER (WHERE t.statut = 'EN_COURS') AS ticketsEnCours, " +
                        "COUNT(*) FILTER (WHERE t.statut = 'RESOLU' OR t.statut = 'FERME') AS ticketsResolu, " +
                        "COALESCE(AVG(EXTRACT(EPOCH FROM (t.updated_at - " +
                        "  COALESCE((SELECT MIN(ta.created_at) FROM ticket_actions ta " +
                        "    WHERE ta.ticket_id = t.id AND ta.action = 'ASSIGNE'), t.created_at)" +
                        ")) / 60) FILTER (WHERE t.statut IN ('RESOLU','FERME')), 0) AS tempsMoyenMinutes, " +
                        "COUNT(*) FILTER (WHERE t.canal = 'CALL') AS ticketsCall, " +
                        "COUNT(*) FILTER (WHERE t.canal = 'EMAIL') AS ticketsEmail, " +
                        "COUNT(*) FILTER (WHERE t.canal = 'WEB') AS ticketsWeb " +
                        "FROM tickets t " +
                        "WHERE t.operateur_id IS NOT NULL " +
                        "AND (t.created_at >= CAST(:dateFrom AS TIMESTAMP) " +
                        "     OR t.updated_at >= CAST(:dateFrom AS TIMESTAMP)) " +
                        "GROUP BY t.operateur_id " +
                        "ORDER BY ticketsTraites DESC", nativeQuery = true)
        List<Object[]> findOperatorPerformanceRaw(@Param("dateFrom") LocalDateTime dateFrom);

        /** Tickets bloqués — EN_COURS depuis plus de X heures */
        @Query(value = "SELECT COUNT(*) FROM tickets t WHERE t.statut = 'EN_COURS' AND t.updated_at < CAST(:threshold AS TIMESTAMP)", nativeQuery = true)
        long countBlockedTickets(@Param("threshold") LocalDateTime threshold);

        /** Tickets non assignés depuis plus de X heures */
        @Query(value = "SELECT COUNT(*) FROM tickets t WHERE t.statut = 'NOUVEAU' AND t.operateur_id IS NULL AND t.created_at < CAST(:threshold AS TIMESTAMP)", nativeQuery = true)
        long countOldUnassignedTickets(@Param("threshold") LocalDateTime threshold);

        /** Total tickets créés depuis une date */
        @Query(value = "SELECT COUNT(*) FROM tickets t WHERE t.created_at > CAST(:dateFrom AS TIMESTAMP)", nativeQuery = true)
        long countCreatedAfter(@Param("dateFrom") LocalDateTime dateFrom);

        /** SLA compliance — tickets résolus en moins de 4 heures */
        @Query(value = "SELECT COUNT(*) FROM tickets t " +
                        "WHERE t.statut IN ('RESOLU','FERME') " +
                        "AND t.created_at >= CAST(:dateFrom AS TIMESTAMP) " +
                        "AND EXTRACT(EPOCH FROM (t.updated_at - t.created_at)) <= 14400", nativeQuery = true)
        long countSlaCompliant(@Param("dateFrom") LocalDateTime dateFrom);

        /** Total tickets résolus/fermés depuis une date */
        @Query(value = "SELECT COUNT(*) FROM tickets t WHERE t.statut IN ('RESOLU','FERME') AND t.created_at >= CAST(:dateFrom AS TIMESTAMP)", nativeQuery = true)
        long countResolvedAfter(@Param("dateFrom") LocalDateTime dateFrom);
}
