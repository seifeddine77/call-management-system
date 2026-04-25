package com.pfe.callmgmt.notification.repository;

import com.pfe.callmgmt.notification.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByStatus(String status, Pageable pageable);

    Page<NotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<NotificationLog> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
}
