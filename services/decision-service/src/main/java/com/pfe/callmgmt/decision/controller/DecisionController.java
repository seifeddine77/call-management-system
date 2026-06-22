package com.pfe.callmgmt.decision.controller;

import com.pfe.callmgmt.decision.dto.DecisionRequest;
import com.pfe.callmgmt.decision.dto.DecisionResult;
import com.pfe.callmgmt.decision.dto.OverrideRequest;
import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.service.DecisionServiceImpl;
import com.pfe.callmgmt.decision.service.LetterGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * API REST d'aide à la décision (decision-service) — scoring d'acceptation des réclamations.
 *
 * <p>Orchestre le pipeline RULES → AI → HYBRID et expose l'analyse (score + décision FAVORABLE/
 * DEFAVORABLE/ETUDE), la finalisation par un superviseur (override) et la génération de courrier.
 * La traçabilité (règles appliquées, source de décision) sert l'explicabilité exigée par le métier.</p>
 */
@RestController
@RequestMapping("/api/decisions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Decision", description = "Module 5 — Aide à la Décision (Scoring IA)")
public class DecisionController {

    private final DecisionServiceImpl decisionService;
    private final LetterGenerationService letterService;

    @PostMapping("/analyze")
    @Operation(summary = "Analyser une demande", description = "Flux 3 étapes : Cache Redis → Moteur de Règles → Gemma 4 (IA locale via Ollama)")
    public ResponseEntity<DecisionResult> analyze(@Valid @RequestBody DecisionRequest request) {
        DecisionResult result = decisionService.analyze(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accepter la recommandation", description = "L'opérateur valide la décision IA")
    public ResponseEntity<DecisionResult> accept(
            @PathVariable("id") UUID id,
            @Valid @RequestBody OverrideRequest request) {
        DecisionResult result = decisionService.acceptDecision(id, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Rejeter la recommandation", description = "L'opérateur contredit la décision IA avec un motif")
    public ResponseEntity<DecisionResult> reject(
            @PathVariable("id") UUID id,
            @Valid @RequestBody OverrideRequest request) {
        DecisionResult result = decisionService.rejectDecision(id, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ticket/{ticketId}")
    @Operation(summary = "Consulter la décision d'un ticket")
    public ResponseEntity<DecisionResult> getByTicketId(@PathVariable("ticketId") UUID ticketId) {
        DecisionResult result = decisionService.getByTicketId(ticketId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/ticket/{ticketId}")
    @Operation(summary = "Supprimer la décision d'un ticket (pour ré-analyse)")
    public ResponseEntity<Void> deleteByTicketId(@PathVariable("ticketId") UUID ticketId) {
        decisionService.deleteByTicketId(ticketId);
        log.info("🗑️ Décision supprimée pour ticket {} — ré-analyse possible", ticketId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/letter")
    @Operation(summary = "Générer et télécharger le courrier PDF")
    public ResponseEntity<byte[]> generateLetter(@PathVariable("id") UUID id) {
        Decision decision = decisionService.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Décision introuvable : " + id));

        // Vérifier qu'une décision finale existe
        String type = decision.getFinalDecision() != null ? decision.getFinalDecision() : decision.getDecision();
        if ("ETUDE".equals(type)) {
            return ResponseEntity.badRequest()
                    .body("Impossible de générer un courrier pour une décision ETUDE. Veuillez d'abord accepter ou rejeter."
                            .getBytes());
        }

        try {
            byte[] pdf = letterService.generateLetter(decision);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("courrier-" + id.toString().substring(0, 8) + ".pdf")
                    .build());

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Erreur génération PDF pour décision {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ── Exception Handlers ──

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
