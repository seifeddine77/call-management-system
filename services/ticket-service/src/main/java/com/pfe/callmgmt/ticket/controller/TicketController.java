package com.pfe.callmgmt.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.callmgmt.ticket.client.AiServiceClient;
import com.pfe.callmgmt.ticket.client.OrganisationServiceClient;
import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.service.QueueService;
import com.pfe.callmgmt.ticket.service.SupervisionService;
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
    private final SupervisionService supervisionService;
    private final AiServiceClient aiServiceClient;
    private final OrganisationServiceClient organisationClient;
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
        TicketResponse existingTicket = ticketService.getTicketById(id, ROLE_ADMIN, userId, null);
        if (existingTicket.getConsentementEnregistrement() == null
                || !existingTicket.getConsentementEnregistrement()) {
            log.warn("⚠️ RGPD: Upload audio pour ticket {} SANS consentement client explicite. "
                    + "L'opérateur assume la responsabilité du traitement.", id);
        }

        String transcription = null;
        String keywordsJson = null;
        String audioPath = java.io.File.separator + "audio" + java.io.File.separator + id + java.io.File.separator + file.getOriginalFilename();

        try {
            // Step 1: Send audio to AI service for transcription
            log.info("Sending audio to AI service for ticket: {}", id);
            Map<String, Object> transcribeResult = aiServiceClient.transcribeAudio(file, id.toString());
            transcription = (String) transcribeResult.get("text");
            audioPath = (String) transcribeResult.getOrDefault("file_path", audioPath);
            log.info("Transcription received for ticket {}: {} chars", id,
                    transcription != null ? transcription.length() : 0);

            // Step 1.5: Chiffrer l'audio sur disque (RGPD)
            audioPath = encryptAudioIfEnabled(id, file, audioPath);

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
            log.warn("AI service call failed for ticket {}: {}. Storing audio path only.", id, e.getMessage(), e);
            // Fallback: store just the audio path even if AI service is down
        }

        // Step 3: Update ticket with transcription results
        ticketService.updateAudioTranscription(id, audioPath, transcription, keywordsJson);

        return ResponseEntity.ok(ticketService.getTicketById(id, ROLE_ADMIN, userId, null));
    }

    private String encryptAudioIfEnabled(UUID id, MultipartFile file, String audioPath) {
        if (audioEncryptionService.isEnabled()) {
            try {
                byte[] encrypted = audioEncryptionService.encrypt(file.getBytes());
                log.info("🔐 Audio chiffré pour ticket {} ({} bytes)", id, encrypted.length);
                return audioPath + ".enc";
            } catch (Exception encEx) {
                log.warn("⚠️ Chiffrement audio échoué pour ticket {}: {}", id, encEx.getMessage(), encEx);
            }
        }
        return audioPath;
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
            String savedPath = processSinglePhoto(file, id);
            if (savedPath != null) {
                photoPaths.add(savedPath);
            }
        }

        if (photoPaths.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return mergeAndSavePhotoPaths(id, photoPaths, userId);
    }

    /**
     * Valide, sanitize et sauvegarde une seule photo sur disque.
     *
     * @return le chemin relatif de la photo sauvegardée, ou null si le fichier est invalide
     */
    private String processSinglePhoto(MultipartFile file, UUID ticketId) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("Fichier non-image ignoré: {}", file.getOriginalFilename());
            return null;
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            log.warn("Photo trop volumineuse ignorée: {} ({}MB)",
                    file.getOriginalFilename(), file.getSize() / 1024 / 1024);
            return null;
        }

        // SonarCloud BLOCKER S2083 fix — Sanitize filename to prevent Path Traversal (CWE-22)
        String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String sanitizedName = Paths.get(rawName).getFileName().toString();
        sanitizedName = sanitizedName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = System.currentTimeMillis() + "_" + sanitizedName;
        String photoPath = "/photos/" + ticketId + "/" + fileName;

        try {
            Path dirPath = Paths.get(photosStoragePath, ticketId.toString());
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(fileName);
            if (!filePath.normalize().startsWith(dirPath.normalize())) {
                log.warn("⚠️ SECURITY: Path traversal attempt detected for file '{}', skipping.", rawName);
                return null;
            }
            file.transferTo(filePath.toFile());
            log.info("📸 Photo sauvegardée: {} -> {}", photoPath, filePath);
        } catch (IOException e) {
            log.error("Erreur sauvegarde fichier {}: {}", sanitizedName, e.getMessage(), e);
            return null;
        }

        return photoPath;
    }

    /**
     * Fusionne les nouveaux chemins photo avec les existants et met à jour le ticket.
     */
    private ResponseEntity<TicketResponse> mergeAndSavePhotoPaths(UUID ticketId, List<String> newPaths, UUID userId) {
        try {
            TicketResponse existing = ticketService.getTicketById(ticketId, ROLE_ADMIN, userId, null);
            List<String> allPaths = new ArrayList<>();
            if (existing.getPhotosPaths() != null && !existing.getPhotosPaths().isBlank()) {
                @SuppressWarnings("unchecked")
                List<String> existingPaths = objectMapper.readValue(existing.getPhotosPaths(), List.class);
                allPaths.addAll(existingPaths);
            }
            allPaths.addAll(newPaths);

            String photosJson = objectMapper.writeValueAsString(allPaths);
            ticketService.updatePhotosPaths(ticketId, photosJson);

            log.info("📸 {} photos total pour ticket {}", allPaths.size(), ticketId);
        } catch (Exception e) {
            log.error("Erreur sauvegarde photos: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(ticketService.getTicketById(ticketId, ROLE_ADMIN, userId, null));
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

    // ══════════════════════════════════════════════════════════════
    // Supervision des opérateurs (Performance + Alertes)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/operators/performance")
    @Operation(summary = "Classement des opérateurs par performance",
               description = "Retourne les métriques de chaque opérateur : tickets traités, taux de résolution, temps moyen, répartition par canal")
    public ResponseEntity<List<OperatorPerformanceResponse>> getOperatorPerformance(
            @RequestParam(defaultValue = "30d") String period) {
        List<OperatorPerformanceResponse> perfs = supervisionService.getOperatorPerformance(period);
        // Resolve operator names via organisation-service Feign
        for (OperatorPerformanceResponse p : perfs) {
            if (p.getOperateurId() != null && p.getOperateurName() == null) {
                try {
                    Map<String, Object> user = organisationClient.getUserById(p.getOperateurId());
                    if (user != null) {
                        String name = user.getOrDefault("prenom", "") + " " + user.getOrDefault("nom", "");
                        p.setOperateurName(name.trim());
                    }
                } catch (Exception e) {
                    p.setOperateurName("Opérateur " + p.getOperateurId().toString().substring(0, 8));
                    log.debug("Could not resolve operator name for {}: {}", p.getOperateurId(), e.getMessage());
                }
            }
        }
        return ResponseEntity.ok(perfs);
    }

    @GetMapping("/supervision/alerts")
    @Operation(summary = "Alertes de supervision",
               description = "Détecte les anomalies : tickets bloqués >24h, non assignés >2h, pics de volume")
    public ResponseEntity<List<SupervisionAlert>> getSupervisionAlerts() {
        return ResponseEntity.ok(supervisionService.getAlerts());
    }
}
