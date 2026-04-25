package com.pfe.callmgmt.notification.controller;

import com.pfe.callmgmt.notification.entity.NotificationLog;
import com.pfe.callmgmt.notification.repository.NotificationLogRepository;
import com.pfe.callmgmt.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    @Test
    @DisplayName("GET /logs — retourne page de logs")
    void getLogs_noFilter_returnsPage() {
        NotificationLog log1 = NotificationLog.builder()
                .id(UUID.randomUUID()).type("WELCOME").status("SENT")
                .recipient("user@test.com").subject("Bienvenue")
                .createdAt(LocalDateTime.now()).build();

        Page<NotificationLog> page = new PageImpl<>(List.of(log1));
        when(logRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<NotificationLog>> response = controller.getLogs(null, 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getContent().get(0).getType()).isEqualTo("WELCOME");
    }

    @Test
    @DisplayName("GET /logs?status=FAILED — filtre par status")
    void getLogs_withStatusFilter_returnsFilteredPage() {
        NotificationLog failedLog = NotificationLog.builder()
                .id(UUID.randomUUID()).type("TICKET_CREATED").status("FAILED")
                .recipient("op@test.com").errorMessage("SMTP error")
                .createdAt(LocalDateTime.now()).build();

        Page<NotificationLog> page = new PageImpl<>(List.of(failedLog));
        when(logRepository.findByStatus(eq("FAILED"), any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<NotificationLog>> response = controller.getLogs("FAILED", 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getContent().get(0).getStatus()).isEqualTo("FAILED");
        verify(logRepository).findByStatus(eq("FAILED"), any());
    }

    @Test
    @DisplayName("GET /logs — pagination respectée")
    void getLogs_pagination_works() {
        Page<NotificationLog> emptyPage = new PageImpl<>(List.of());
        when(logRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(emptyPage);

        ResponseEntity<Page<NotificationLog>> response = controller.getLogs(null, 5, 10);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    @DisplayName("POST /retry/{id} — retry réussi retourne 200")
    void retry_success_returns200() {
        UUID id = UUID.randomUUID();
        NotificationLog retried = NotificationLog.builder()
                .id(id).status("SENT").attempts((short) 2).build();

        when(notificationService.retry(id)).thenReturn(retried);

        ResponseEntity<NotificationLog> response = controller.retry(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("SENT");
        assertThat(response.getBody().getAttempts()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("POST /retry/{id} — notification inexistante lève exception")
    void retry_notFound_throwsException() {
        UUID fakeId = UUID.randomUUID();
        when(notificationService.retry(fakeId)).thenThrow(new NoSuchElementException("Not found"));

        assertThatThrownBy(() -> controller.retry(fakeId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("GET /health — retourne OK")
    void health_returnsOk() {
        ResponseEntity<String> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("OK");
    }
}
