package com.pfe.callmgmt.notification.controller;

import com.pfe.callmgmt.notification.entity.NotificationLog;
import com.pfe.callmgmt.notification.repository.NotificationLogRepository;
import com.pfe.callmgmt.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API REST du journal de notifications (notification-service).
 *
 * <p>Expose l'historique des notifications email envoyées (consultation ADMIN). L'envoi lui-même
 * est piloté de façon asynchrone par consommation d'événements RabbitMQ (ticket/décision), ce qui
 * découple la notification du chemin critique de traitement.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Logs et gestion des notifications email")
public class NotificationController {

    private final NotificationLogRepository logRepository;
    private final NotificationService notificationService;

    @GetMapping("/logs")
    @Operation(summary = "Liste des logs de notifications (paginé)")
    public ResponseEntity<Page<NotificationLog>> getLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(logRepository.findByStatus(status, pageRequest));
        }
        return ResponseEntity.ok(logRepository.findAllByOrderByCreatedAtDesc(pageRequest));
    }

    @PostMapping("/retry/{id}")
    @Operation(summary = "Relancer une notification échouée")
    public ResponseEntity<NotificationLog> retry(@PathVariable UUID id) {
        NotificationLog result = notificationService.retry(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
