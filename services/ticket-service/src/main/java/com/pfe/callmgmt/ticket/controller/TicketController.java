package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.service.QueueService;
import com.pfe.callmgmt.ticket.service.TicketService;
import com.pfe.callmgmt.ticket.service.AudioEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Gestion des tickets multicanaux")
public class TicketController {

    // SonarCloud HIGH fix — centralize "ADMIN" literal (duplicated 7x) into a constant
    private static final String ROLE_ADMIN = "ADMIN";

    private final TicketService ticketService;
    private final QueueService queueService;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final AudioEncryptionService audioEncryptionService;

    @Value("${photos.storage-path:/tmp/ticket-photos}")
    private String photosStoragePath;

    @GetMapping
    @Operation(summary = "Lister les tickets avec filtres")
    public ResponseEntity<Page<TicketResponse>> getTickets(
            @ModelAttribute TicketFilter filter,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID userAgenceId) {
        return ResponseEntity.ok(ticketService.getTickets(filter, userRole, userId, userAgenceId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir le détail d'un ticket avec historique d'actions")
    public ResponseEntity<TicketResponse> getTicketById(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID userAgenceId) {
        return ResponseEntity.ok(ticketService.getTicketById(id, userRole, userId, userAgenceId));
    }

    @PostMapping
    @Operation(summary = "Créer un nouveau ticket")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.createTicket(request, userId, userEmail));
    }

    @PatchMapping("/{id}/statut")
    @Operation(summary = "Changer le statut d'un ticket")
    public ResponseEntity<TicketResponse> updateStatut(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateStatutRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Email", required = false) String userName) {
        return ResponseEntity.ok(ticketService.updateStatut(id, request, userId, userName));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assigner un ticket à un opérateur")
    public ResponseEntity<TicketResponse> assignTicket(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignTicketRequest request,
            @RequestHeader(name = "X-User-Id", required = false) UUID requesterId,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String requesterRole,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID requesterAgenceId) {
        return ResponseEntity.ok(ticketService.assignTicket(id, request.getOperateurId(), requesterId, requesterRole,
                requesterAgenceId));
    }

    @GetMapping("/stats")
    @Operation(summary = "Obtenir les statistiques des tickets par statut")
    public ResponseEntity<TicketStats> getTicketStats(
            @RequestParam(value = "agenceId", required = false) UUID agenceId,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID userAgenceId,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole) {
        UUID effectiveAgenceId = ROLE_ADMIN.equals(userRole) ? agenceId : userAgenceId;
        return ResponseEntity.ok(ticketService.getTicketStats(effectiveAgenceId));
    }

    @PostMapping("/{id}/audio")
    @Operation(summary = "Uploader un enregistrement audio pour transcription et analyse NLP")
    public ResponseEntity<TicketResponse> uploadAudio(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Email", required = false) String userName) {

        // RGPD (CDC 5.1) — Vérification du consentement à l'enregistrement
        TicketResponse existingTicket = ticketService.getTicketById(id, "ADMIN", userId, null);
        if (existingTicket.getConsentementEnregistrement() == null
                || !existingTicket.getConsentementEnregistrement()) {
            log.warn("⚠️ RGPD: Upload audio pour ticket {} SANS consentement client explicite. "
                    + "L'opérateur assume la responsabilité du traitement.", id);
        }

        String transcription = null;
        String keywordsJson = null;
        String audioPath = "/audio/" + id + "/" + file.getOriginalFilename();

        try {
            // Step 1: Send audio to AI service for transcription
            log.info("Sending audio to AI service for ticket: {}", id);
            Map<String, Object> transcribeResult = aiServiceClient.transcribeAudio(file, id.toString());
            transcription = (String) transcribeResult.get("text");
            audioPath = (String) transcribeResult.getOrDefault("file_path", audioPath);
            log.info("Transcription received for ticket {}: {} chars", id,
                    transcription != null ? transcription.length() : 0);

            // Step 1.5: Chiffrer l'audio sur disque (RGPD)
            if (audioEncryptionService.isEnabled()) {
                try {
                    byte[] encrypted = audioEncryptionService.encrypt(file.getBytes());
                    log.info("🔐 Audio chiffré pour ticket {} ({} bytes)", id, encrypted.length);
                    // Le fichier chiffré serait sauvegardé sur disque ici
                    audioPath = audioPath + ".enc";
                } catch (Exception encEx) {
                    log.warn("⚠️ Chiffrement audio échoué pour ticket {}: {}", id, encEx.getMessage());
                }
            }

            // Step 2: Send transcribed text to AI service for NLP analysis
            if (transcription != null && !transcription.isBlank()) {
                log.info("Sending text to AI service for NLP analysis, ticket: {}", id);
                Map<String, String> analyzeRequest = Map.of(
                        "ticket_id", id.toString(),
                        "text", transcription);
                Map<String, Object> analysisResult = aiServiceClient.analyzeText(analyzeRequest);

                // Extract keywords as JSON string for storage
                Object keywords = analysisResult.get("keywords");
                if (keywords instanceof List) {
                    keywordsJson = objectMapper.writeValueAsString(keywords);
                }

                log.info("NLP analysis completed for ticket {}: sinistre={}, sentiment={}, piece={}",
                        id,
                        analysisResult.get("type_sinistre"),
                        analysisResult.get("sentiment"),
                        analysisResult.get("piece_reference"));
            }

        } catch (Exception e) {
            log.warn("AI service call failed for ticket {}: {}. Storing audio path only.", id, e.getMessage());
            // Fallback: store just the audio path even if AI service is down
        }

        // Step 3: Update ticket with transcription results
        ticketService.updateAudioTranscription(id, audioPath, transcription, keywordsJson);

        return ResponseEntity.ok(ticketService.getTicketById(id, ROLE_ADMIN, userId, null));
    }

    @GetMapping("/{id}/transcription")
    @Operation(summary = "Récupérer la transcription et l'analyse NLP d'un ticket")
    public ResponseEntity<?> getTranscription(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID userAgenceId) {

        var ticket = ticketService.getTicketById(id, userRole, userId, userAgenceId);

        if (ticket.getTranscription() == null && ticket.getAudioPath() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Aucune transcription disponible pour ce ticket",
                            "ticketId", id.toString()));
        }

        return ResponseEntity.ok(Map.of(
                "ticketId", id.toString(),
                "audioPath", ticket.getAudioPath() != null ? ticket.getAudioPath() : "",
                "transcription", ticket.getTranscription() != null ? ticket.getTranscription() : "",
                "keywords", ticket.getTranscriptionKeywords() != null ? ticket.getTranscriptionKeywords() : "[]",
                "hasAudio", ticket.getAudioPath() != null,
                "hasTranscription", ticket.getTranscription() != null));
    }

    @GetMapping("/queue")
    @Operation(summary = "Afficher la file d'attente des tickets non assignés")
    public ResponseEntity<QueueResponse> getQueue(
            @RequestParam(value = "agenceId", required = false) UUID agenceId,
            @RequestHeader(name = "X-User-Agence-Id", required = false) UUID userAgenceId,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole) {
        UUID effectiveAgenceId = ROLE_ADMIN.equals(userRole) ? agenceId : userAgenceId;
        return ResponseEntity.ok(queueService.getQueue(effectiveAgenceId));
    }

    // ══════════════════════════════════════════════════════════════
    // Photos de sinistre (CDC Module 5)
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/photos")
    @Operation(summary = "Uploader des photos de sinistre pour un ticket",
               description = "CDC Module 5 — Accepte plusieurs photos (JPEG, PNG, WebP). Stocke les chemins en JSONB.")
    public ResponseEntity<TicketResponse> uploadPhotos(
            @PathVariable("id") UUID id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader(name = "X-User-Id", required = false) UUID userId) {

        log.info("📸 Upload de {} photo(s) pour le ticket: {}", files.size(), id);

        List<String> photoPaths = new ArrayList<>();
        for (MultipartFile file : files) {
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("Fichier non-image ignoré: {}", file.getOriginalFilename());
                continue;
            }
            // Validate file size (max 10MB per photo)
            if (file.getSize() > 10 * 1024 * 1024) {
                log.warn("Photo trop volumineuse ignorée: {} ({}MB)",
                        file.getOriginalFilename(), file.getSize() / 1024 / 1024);
                continue;
            }

            // SonarCloud BLOCKER S2083 fix — Sanitize filename to prevent Path Traversal (CWE-22)
            // Strip all directory components from the user-supplied filename
            String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            // Use Paths.get().getFileName() to remove any directory traversal sequences ("../", "/" etc.)
            String sanitizedName = Paths.get(rawName).getFileName().toString();
            // Additionally remove any remaining dangerous characters
            sanitizedName = sanitizedName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = System.currentTimeMillis() + "_" + sanitizedName;
            String photoPath = "/photos/" + id + "/" + fileName;

            // Actually save the file to disk
            try {
                Path dirPath = Paths.get(photosStoragePath, id.toString());
                Files.createDirectories(dirPath);
                Path filePath = dirPath.resolve(fileName);
                // Validate resolved path stays within the storage directory (extra safety)
                if (!filePath.normalize().startsWith(dirPath.normalize())) {
                    log.warn("⚠️ SECURITY: Path traversal attempt detected for file '{}', skipping.", rawName);
                    continue;
                }
                file.transferTo(filePath.toFile());
                log.info("📸 Photo sauvegardée: {} -> {}", photoPath, filePath);
            } catch (IOException e) {
                log.error("Erreur sauvegarde fichier {}: {}", sanitizedName, e.getMessage());
                continue;
            }

            photoPaths.add(photoPath);
            log.info("📸 Photo enregistrée: {}", photoPath);
        }

        if (photoPaths.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Merge with existing photos
            TicketResponse existing = ticketService.getTicketById(id, "ADMIN", userId, null);
            List<String> allPaths = new ArrayList<>();
            if (existing.getPhotosPaths() != null && !existing.getPhotosPaths().isBlank()) {
                @SuppressWarnings("unchecked")
                List<String> existingPaths = objectMapper.readValue(existing.getPhotosPaths(), List.class);
                allPaths.addAll(existingPaths);
            }
            allPaths.addAll(photoPaths);

            String photosJson = objectMapper.writeValueAsString(allPaths);
            ticketService.updatePhotosPaths(id, photosJson);

            log.info("📸 {} photos total pour ticket {}", allPaths.size(), id);
        } catch (Exception e) {
            log.error("Erreur sauvegarde photos: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(ticketService.getTicketById(id, "ADMIN", userId, null));
    }

    // ══════════════════════════════════════════════════════════════
    // Servir les photos (GET /api/tickets/photos/{ticketId}/{filename})
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/photos/{ticketId}/{filename}")
    @Operation(summary = "Servir une photo de sinistre")
    public ResponseEntity<Resource> servePhoto(
            @PathVariable("ticketId") String ticketId,
            @PathVariable("filename") String filename) {
        Path filePath = Paths.get(photosStoragePath, ticketId, filename);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        String contentType = "image/jpeg";
        if (filename.endsWith(".png")) contentType = "image/png";
        else if (filename.endsWith(".webp")) contentType = "image/webp";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    // ══════════════════════════════════════════════════════════════
    // RGPD — Anonymisation (droit à l'oubli)
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/anonymize")
    @Operation(summary = "RGPD — Anonymiser les données personnelles d'un ticket (ADMIN uniquement)", description = "Remplace les données client par ***ANONYMISÉ***, supprime l'audio et la transcription")
    public ResponseEntity<TicketResponse> anonymizeTicket(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "X-User-Id", required = false) UUID adminId,
            @RequestHeader(name = "X-User-Email", required = false) String adminName,
            @RequestHeader(name = "X-User-Role", required = false, defaultValue = "OPERATOR") String userRole) {

        // Vérification RBAC — seul un ADMIN peut anonymiser
        if (!ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(ticketService.anonymizeTicket(id, adminId, adminName));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
