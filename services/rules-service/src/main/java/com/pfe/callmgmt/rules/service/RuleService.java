package com.pfe.callmgmt.rules.service;

import com.pfe.callmgmt.rules.dto.*;
import com.pfe.callmgmt.rules.entity.Rule;
import com.pfe.callmgmt.rules.entity.RuleVersion;
import com.pfe.callmgmt.rules.repository.RuleRepository;
import com.pfe.callmgmt.rules.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de gestion des règles d'assurance (CDC Module 4)
 *
 * Fonctionnalités :
 * - CRUD complet (4.A)
 * - Versioning automatique (4.D)
 * - Évaluation des demandes (4.E/4.F)
 * - Toggle activation/désactivation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final RuleEvaluator ruleEvaluator;

    // ============================
    // CRUD Operations
    // ============================

    /**
     * Créer une nouvelle règle d'assurance
     */
    @Transactional
    public RuleResponse createRule(CreateRuleRequest request, UUID userId, String userName) {
        Rule rule = Rule.builder()
                .nom(request.getNom())
                .description(request.getDescription())
                .conditions(request.getConditions())
                .action(request.getAction().toUpperCase())
                .actionMotif(request.getActionMotif())
                .priorite(request.getPriorite() != null ? request.getPriorite() : 5)
                .actif(true)
                .version((short) 1)
                .createdBy(userId)
                .createdByName(userName)
                .build();

        rule = ruleRepository.save(rule);
        log.info("✅ Règle créée: '{}' (id={}, action={}, priorité={})",
                rule.getNom(), rule.getId(), rule.getAction(), rule.getPriorite());

        return toResponse(rule);
    }

    /**
     * Modifier une règle existante (CDC 4.D — Versioning automatique)
     * L'ancienne version est sauvegardée dans rule_versions AVANT modification.
     */
    @Transactional
    public RuleResponse updateRule(UUID ruleId, UpdateRuleRequest request, UUID userId, String userName) {
        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Règle non trouvée: " + ruleId));

        // Sauvegarder l'ancienne version avant modification
        RuleVersion version = RuleVersion.builder()
                .ruleId(rule.getId())
                .conditions(rule.getConditions())
                .action(rule.getAction())
                .actionMotif(rule.getActionMotif())
                .priorite(rule.getPriorite())
                .version(rule.getVersion())
                .changedBy(userId)
                .changedByName(userName)
                .build();
        ruleVersionRepository.save(version);
        log.info("📋 Version {} de la règle '{}' archivée", rule.getVersion(), rule.getNom());

        // Appliquer les modifications
        rule.setNom(request.getNom());
        rule.setDescription(request.getDescription());
        rule.setConditions(request.getConditions());
        rule.setAction(request.getAction().toUpperCase());
        rule.setActionMotif(request.getActionMotif());
        if (request.getPriorite() != null) {
            rule.setPriorite(request.getPriorite());
        }
        rule.setVersion((short) (rule.getVersion() + 1));
        rule.setUpdatedAt(LocalDateTime.now());

        rule = ruleRepository.save(rule);
        log.info("✅ Règle '{}' mise à jour → version {}", rule.getNom(), rule.getVersion());

        return toResponse(rule);
    }

    /**
     * Supprimer une règle (ADMIN uniquement — CDC 4.F)
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Règle non trouvée: " + ruleId));
        ruleRepository.delete(rule);
        log.info("🗑️ Règle '{}' supprimée (id={})", rule.getNom(), ruleId);
    }

    /**
     * Trouver une règle par ID
     */
    public RuleResponse findById(UUID ruleId) {
        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Règle non trouvée: " + ruleId));
        return toResponse(rule);
    }

    /**
     * Lister toutes les règles avec pagination et filtre actif
     */
    public Page<RuleResponse> findAll(Boolean actif, Pageable pageable) {
        if (actif != null) {
            return ruleRepository.findByActif(actif, pageable).map(this::toResponse);
        }
        return ruleRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Activer/Désactiver une règle (CDC 4.A — Toggle)
     */
    @Transactional
    public RuleResponse toggleRule(UUID ruleId) {
        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Règle non trouvée: " + ruleId));

        rule.setActif(!rule.getActif());
        rule.setUpdatedAt(LocalDateTime.now());
        rule = ruleRepository.save(rule);

        log.info("🔄 Règle '{}' → {}", rule.getNom(), rule.getActif() ? "ACTIVÉE" : "DÉSACTIVÉE");
        return toResponse(rule);
    }

    /**
     * Historique des versions d'une règle (CDC 4.D)
     */
    public List<RuleVersion> getHistory(UUID ruleId) {
        // Vérifier que la règle existe
        ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Règle non trouvée: " + ruleId));
        return ruleVersionRepository.findByRuleIdOrderByVersionDesc(ruleId);
    }

    // ============================
    // Évaluation des règles (CDC 4.E)
    // ============================

    /**
     * Évalue une demande contre TOUTES les règles actives.
     *
     * Algorithme :
     * 1. Récupérer toutes les règles actives (triées par priorité DESC)
     * 2. Évaluer chaque règle avec les données métier
     * 3. Collecter les règles matchées
     * 4. Calculer le score et la décision finale :
     * - Si toutes les règles matchées → même action → score élevé, décision claire
     * - Si conflit (FAVORABLE vs DEFAVORABLE) → ambiguous=true, action=ETUDE
     * - Si aucune règle ne matche → ambiguous=true, score faible
     */
    @Transactional(readOnly = true)
    public EvaluateResponse evaluate(EvaluateRequest request) {
        List<Rule> activeRules = ruleRepository.findByActifTrueOrderByPrioriteDesc();

        if (activeRules.isEmpty()) {
            log.warn("⚠️ Aucune règle active — évaluation impossible");
            return EvaluateResponse.builder()
                    .decision("ETUDE")
                    .score(0.0)
                    .motif("Aucune règle active configurée. Évaluation manuelle requise.")
                    .rulesApplied(List.of())
                    .totalRulesEvaluated(0)
                    .matchedRules(0)
                    .ambiguous(true)
                    .build();
        }

        // Évaluer chaque règle
        List<Rule> matchedRules = new ArrayList<>();
        for (Rule rule : activeRules) {
            boolean matches = ruleEvaluator.evaluate(rule.getConditions(), request);
            if (matches) {
                matchedRules.add(rule);
                log.debug("✅ Règle '{}' matche (priorité={}, action={})",
                        rule.getNom(), rule.getPriorite(), rule.getAction());
            }
        }

        // Aucune règle ne matche
        if (matchedRules.isEmpty()) {
            log.info("📊 Évaluation: 0/{} règles matchées → ETUDE", activeRules.size());
            return EvaluateResponse.builder()
                    .decision("ETUDE")
                    .score(30.0)
                    .motif("Aucune règle d'assurance ne correspond à cette demande. Analyse IA complémentaire recommandée.")
                    .rulesApplied(List.of())
                    .totalRulesEvaluated(activeRules.size())
                    .matchedRules(0)
                    .ambiguous(true)
                    .build();
        }

        // Analyser les résultats
        List<String> rulesAppliedNames = matchedRules.stream()
                .map(Rule::getNom)
                .collect(Collectors.toList());

        Map<String, Long> actionCounts = matchedRules.stream()
                .collect(Collectors.groupingBy(Rule::getAction, Collectors.counting()));

        boolean hasFavorable = actionCounts.containsKey("FAVORABLE");
        boolean hasDefavorable = actionCounts.containsKey("DEFAVORABLE");
        // Note: le cas ETUDE est géré par le else final (toutes les règles sont ETUDE)

        // Déterminer la décision finale
        String finalDecision;
        double score;
        String motif;
        boolean ambiguous;

        if (hasFavorable && hasDefavorable) {
            // Conflit → la règle de plus haute priorité gagne
            Rule highestPriority = matchedRules.get(0); // Déjà triées par priorité DESC
            finalDecision = highestPriority.getAction();
            double conflictBase = 55.0;
            double transcriptionBonus = analyzeTranscriptionKeywords(request.getTranscriptionText());
            score = Math.min(70.0, Math.max(35.0, conflictBase + transcriptionBonus));
            motif = String.format(
                    "Conflit entre règles (%d FAVORABLE, %d DÉFAVORABLE). Décision basée sur la règle la plus prioritaire '%s'. Vérification IA recommandée.",
                    actionCounts.getOrDefault("FAVORABLE", 0L),
                    actionCounts.getOrDefault("DEFAVORABLE", 0L),
                    highestPriority.getNom());
            ambiguous = true;

        } else if (hasFavorable && !hasDefavorable) {
            finalDecision = "FAVORABLE";
            // Formule enrichie : base + bonus règles + bonus age/km
            double baseScore = 55.0;
            double rulesBonus = Math.min(20.0, matchedRules.size() * 8.0);
            double ageBonus = 0.0;
            double kmBonus = 0.0;
            if (request.getAgeVehiculeAns() != null) {
                ageBonus = request.getAgeVehiculeAns() <= 3 ? 10.0 : request.getAgeVehiculeAns() <= 5 ? 5.0 : -5.0;
            }
            if (request.getKilometrage() != null) {
                kmBonus = request.getKilometrage() <= 50000 ? 8.0 : request.getKilometrage() <= 100000 ? 3.0 : -5.0;
            }
            // Bonus catégorie client
            double clientBonus = 0.0;
            if ("VIP".equalsIgnoreCase(request.getCategorieClient()))
                clientBonus = 5.0;
            else if ("PREMIUM".equalsIgnoreCase(request.getCategorieClient()))
                clientBonus = 3.0;
            // Bonus/malus mots-clés transcription audio (CDC: "mots-clés transcription
            // audio")
            double transcriptionBonus = analyzeTranscriptionKeywords(request.getTranscriptionText());
            score = Math.min(98.0,
                    Math.max(40.0, baseScore + rulesBonus + ageBonus + kmBonus + clientBonus + transcriptionBonus));
            motif = matchedRules.get(0).getActionMotif();
            ambiguous = false;

        } else if (hasDefavorable && !hasFavorable) {
            finalDecision = "DEFAVORABLE";
            // Formule enrichie pour DEFAVORABLE
            double baseScore = 55.0;
            double rulesBonus = Math.min(20.0, matchedRules.size() * 8.0);
            double ageMalus = 0.0;
            if (request.getAgeVehiculeAns() != null) {
                ageMalus = request.getAgeVehiculeAns() > 10 ? 10.0 : request.getAgeVehiculeAns() > 5 ? 5.0 : 0.0;
            }
            // Bonus/malus mots-clés transcription audio (CDC: "mots-clés transcription
            // audio")
            double transcriptionBonus = analyzeTranscriptionKeywords(request.getTranscriptionText());
            score = Math.min(98.0, Math.max(40.0, baseScore + rulesBonus + ageMalus + transcriptionBonus));
            motif = matchedRules.get(0).getActionMotif();
            ambiguous = false;

        } else {
            // Toutes les règles sont ETUDE
            finalDecision = "ETUDE";
            score = 45.0;
            motif = matchedRules.get(0).getActionMotif();
            ambiguous = true;
        }

        log.info("📊 Évaluation: {}/{} règles matchées → {} (score={}, ambiguous={})",
                matchedRules.size(), activeRules.size(), finalDecision, score, ambiguous);

        return EvaluateResponse.builder()
                .decision(finalDecision)
                .score(score)
                .motif(motif)
                .rulesApplied(rulesAppliedNames)
                .totalRulesEvaluated(activeRules.size())
                .matchedRules(matchedRules.size())
                .ambiguous(ambiguous)
                .build();
    }

    // ============================
    // Analyse mots-clés transcription (CDC Module 5)
    // ============================

    /**
     * Analyse les mots-clés de la transcription audio pour ajuster le score.
     * CDC: "Analyse des données + mots-clés transcription audio"
     *
     * Domaine : Assurance pièces de rechange automobile
     * Les mots-clés sont pondérés selon 4 catégories métier :
     * 1. Garantie / couverture (+4) → indices de prise en charge légitime
     * 2. Entretien / usage (+3) → bon comportement client
     * 3. Risque / exclusion (-5) → facteurs d'exclusion de garantie
     * 4. Fraude / abus (-7) → signaux de fraude potentielle
     */
    private double analyzeTranscriptionKeywords(String transcriptionText) {
        if (transcriptionText == null || transcriptionText.isBlank())
            return 0.0;

        String text = transcriptionText.toLowerCase();
        double bonus = 0.0;
        List<String> matched = new ArrayList<>();

        // ── 1. GARANTIE & COUVERTURE (+4 par mot-clé) ──
        // Le client mentionne des termes liés à une réclamation légitime
        String[] garantieKeywords = {
                "sous garantie", "garantie constructeur", "garantie pièce",
                "défaut de fabrication", "vice caché", "pièce défectueuse",
                "usure normale", "usure prématurée", "panne mécanique",
                "défaillance", "casse moteur", "pièce cassée",
                "remplacement sous garantie", "prise en charge"
        };
        for (String kw : garantieKeywords) {
            if (text.contains(kw)) {
                bonus += 4.0;
                matched.add("+" + kw);
            }
        }

        // ── 2. ENTRETIEN & USAGE NORMAL (+3 par mot-clé) ──
        // Le client montre un comportement responsable
        String[] entretienKeywords = {
                "entretien régulier", "révision", "carnet d'entretien",
                "vidange", "contrôle technique", "kilométrage normal",
                "usage normal", "usage quotidien", "véhicule bien entretenu",
                "facture entretien", "garagiste agréé", "concessionnaire"
        };
        for (String kw : entretienKeywords) {
            if (text.contains(kw)) {
                bonus += 3.0;
                matched.add("+" + kw);
            }
        }

        // ── 3. RISQUE & EXCLUSION DE GARANTIE (-5 par mot-clé) ──
        // Situations généralement exclues de la couverture
        String[] risqueKeywords = {
                "accident", "collision", "choc", "accrochage",
                "mauvais entretien", "pas d'entretien", "négligence",
                "surchauffe", "surrégime", "mauvaise utilisation",
                "pièce non d'origine", "pièce adaptable", "modification moteur",
                "kilométrage élevé", "véhicule ancien",
                "conduite sportive", "circuit", "course"
        };
        for (String kw : risqueKeywords) {
            if (text.contains(kw)) {
                bonus -= 5.0;
                matched.add("-" + kw);
            }
        }

        // ── 4. FRAUDE & ABUS (-7 par mot-clé) ──
        // Signaux forts de fraude ou tentative d'abus
        String[] fraudeKeywords = {
                "fausse déclaration", "faux document", "compteur trafiqué",
                "kilométrage falsifié", "dommage volontaire", "auto-sabotage",
                "réclamation multiple", "sinistre inventé", "frauduleux",
                "tuning", "reprogrammation moteur", "chiptuning"
        };
        for (String kw : fraudeKeywords) {
            if (text.contains(kw)) {
                bonus -= 7.0;
                matched.add("⚠" + kw);
            }
        }

        // Limiter l'impact entre -20 et +20
        bonus = Math.max(-20.0, Math.min(20.0, bonus));
        if (!matched.isEmpty()) {
            log.info("📝 Analyse transcription: bonus={}, mots-clés={}", bonus, matched);
        }
        return bonus;
    }

    // ============================
    // Mapping
    // ============================

    private RuleResponse toResponse(Rule rule) {
        return RuleResponse.builder()
                .id(rule.getId())
                .nom(rule.getNom())
                .description(rule.getDescription())
                .conditions(rule.getConditions())
                .action(rule.getAction())
                .actionMotif(rule.getActionMotif())
                .priorite(rule.getPriorite())
                .actif(rule.getActif())
                .version(rule.getVersion())
                .createdBy(rule.getCreatedBy())
                .createdByName(rule.getCreatedByName())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
