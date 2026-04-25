package com.pfe.callmgmt.rules.controller;

import com.pfe.callmgmt.rules.dto.*;
import com.pfe.callmgmt.rules.entity.RuleVersion;
import com.pfe.callmgmt.rules.service.ReferenceDataService;
import com.pfe.callmgmt.rules.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST pour la gestion des règles d'assurance (CDC Module 4.F)
 *
 * Endpoints :
 * GET /api/rules → liste paginée
 * GET /api/rules/{id} → détail règle
 * GET /api/rules/{id}/history → historique versions
 * POST /api/rules → créer règle
 * PUT /api/rules/{id} → modifier règle (+ versioning)
 * DELETE /api/rules/{id} → supprimer règle
 * PATCH /api/rules/{id}/toggle → activer/désactiver
 * POST /api/rules/evaluate → évaluer données vs règles actives
 */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Règles d'Assurance", description = "CDC Module 4 — Moteur de règles JSONB")
public class RuleController {

    private final RuleService ruleService;
    private final ReferenceDataService referenceDataService;

    // ----------------------------
    // CRUD
    // ----------------------------

    @GetMapping
    @Operation(summary = "Lister les règles", description = "Liste paginée avec filtre optionnel actif/tous")
    public ResponseEntity<Page<RuleResponse>> list(
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 20, sort = "priorite", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ruleService.findAll(actif, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une règle")
    public ResponseEntity<RuleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ruleService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Créer une règle", description = "SUPERVISOR+ uniquement")
    public ResponseEntity<RuleResponse> create(
            @Valid @RequestBody CreateRuleRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        UUID uid = userId != null ? UUID.fromString(userId) : null;
        String uname = userName != null ? userName : "ANONYMOUS";

        RuleResponse response = ruleService.createRule(request, uid, uname);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une règle", description = "Déclenche le versioning automatique (CDC 4.D)")
    public ResponseEntity<RuleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRuleRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        UUID uid = userId != null ? UUID.fromString(userId) : null;
        String uname = userName != null ? userName : "ANONYMOUS";

        RuleResponse response = ruleService.updateRule(id, request, uid, uname);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une règle", description = "ADMIN uniquement")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activer/Désactiver une règle")
    public ResponseEntity<RuleResponse> toggle(@PathVariable UUID id) {
        return ResponseEntity.ok(ruleService.toggleRule(id));
    }

    // ----------------------------
    // Versioning (CDC 4.D)
    // ----------------------------

    @GetMapping("/{id}/history")
    @Operation(summary = "Historique des versions", description = "Liste des versions précédentes de la règle")
    public ResponseEntity<List<RuleVersion>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(ruleService.getHistory(id));
    }

    // ----------------------------
    // Paramétrage (CDC 4.B)
    // ----------------------------

    @GetMapping("/references")
    @Operation(summary = "Listes de référence", description = "CDC 4.B — Retourne les types de pièces, catégories clients, types de sinistres, opérateurs et champs disponibles pour l'éditeur de règles")
    public ResponseEntity<ReferenceDataResponse> references() {
        return ResponseEntity.ok(referenceDataService.getReferenceData());
    }

    // ----------------------------
    // Évaluation (CDC 4.E/4.F)
    // ----------------------------

    @PostMapping("/evaluate")
    @Operation(summary = "Évaluer une demande", description = "Évalue les données métier contre toutes les règles actives. Retourne FAVORABLE/DEFAVORABLE/ETUDE avec score et motif.")
    public ResponseEntity<EvaluateResponse> evaluate(@Valid @RequestBody EvaluateRequest request) {
        log.info("📊 Évaluation demandée: typePiece={}, typeSinistre={}, ageVehicule={} ans, km={}",
                request.getTypePiece(), request.getTypeSinistre(),
                request.getAgeVehiculeAns(), request.getKilometrage());

        EvaluateResponse response = ruleService.evaluate(request);

        log.info("📊 Résultat: {} (score={}, {} règles matchées, ambiguous={})",
                response.getDecision(), response.getScore(),
                response.getMatchedRules(), response.getAmbiguous());

        return ResponseEntity.ok(response);
    }
}
