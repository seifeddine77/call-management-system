-- ============================================================================
-- V11 — Corrections d'expertise + enrichissement du moteur de règles
--
-- Contexte : audit du moteur sur la base live (2026-06-20). Le moteur évalue
-- les règles ACTIVES triées par priorité DESC ; en cas de conflit FAVORABLE/
-- DEFAVORABLE, **la priorité la PLUS HAUTE l'emporte** (RuleService.evaluate,
-- matchedRules.get(0)). Une règle ETUDE qui matche agit comme un VETO
-- (force la mise à l'étude). Le score = base (65 FAV / 35 DEFAV / 45 ETUDE)
-- + somme des score_impact (clamp ±50) + bonus mots-clés transcription.
--
-- Ce script :
--   PARTIE A — corrige 2 défauts avérés (preuve par appel /evaluate)
--   PARTIE B — comble les lacunes de couverture (collision, usure générale)
--   PARTIE C — clarifie les mises à l'étude manquantes
-- ============================================================================


-- ────────────────────────────────────────────────────────────────────────────
-- PARTIE A — CORRECTIONS
-- ────────────────────────────────────────────────────────────────────────────

-- A1. Suppression de « Exclusion collision pièce mécanique » (2 lignes).
--
--   Défaut métier : un dommage MÉCANIQUE consécutif à un ACCIDENT est couvert
--   au titre de la garantie collision. Or cette règle le refusait (DEFAVORABLE).
--   Défaut technique : V10 avait porté sa priorité à 8 « pour laisser la règle
--   favorable s'appliquer » — mais 8 > 2, donc l'exclusion ÉCRASAIT la règle
--   « Réparation mécanique post-accident récent » (priorité 2). Preuve live :
--     ACCIDENT+MECANIQUE, 3 ans, 40 000 km  →  DEFAVORABLE (score 20)  [FAUX]
--   L'une des 2 lignes est en outre cassée : condition « ageVehiculeAns > 150000 »
--   (l'âge en ANNÉES comparé à 150000 — visait le kilométrage) ne matche jamais.
--
--   Après suppression : ACCIDENT+MECANIQUE récent → FAVORABLE (règle prio 2),
--   ACCIDENT+MECANIQUE ancien (>8 ans) → ETUDE (règle existante). Comportement correct.
DELETE FROM rules WHERE nom = 'Exclusion collision pièce mécanique';

-- A2. Suppression de la règle incohérente « Exclusion véhicule très ancien » au
--   format hérité (action=FAVORABLE alors que le nom dit « exclusion », condition
--   sur FREINAGE sans rapport, déjà inactive). La vraie règle d'exclusion des
--   véhicules > 15 ans (priorité 10, DEFAVORABLE) est conservée.
DELETE FROM rules WHERE nom = 'Exclusion véhicule très ancien' AND action = 'FAVORABLE';


-- ────────────────────────────────────────────────────────────────────────────
-- PARTIE B — ENRICHISSEMENT : LACUNES DE COUVERTURE
-- ────────────────────────────────────────────────────────────────────────────

-- B1. Prise en charge des dommages ACCIDENTELS sur pièces diverses.
--   Lacune live : ACCIDENT+FREINAGE (et suspension, transmission, pneumatique,
--   hydraulique, climatisation) → ETUDE faute de règle. Or tout dommage
--   consécutif à un accident relève de la garantie collision.
--   Priorité 2 (alignée sur les autres règles « réparation post-accident »).
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Prise en charge dommages accidentels (pièces diverses)',
    'Tout composant (freinage, suspension, transmission, pneumatique, hydraulique, climatisation) endommagé à la suite d''un accident est couvert au titre de la garantie collision.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "ACCIDENT"},
        {"field": "typePiece", "op": "IN", "value": ["FREINAGE", "SUSPENSION", "TRANSMISSION", "PNEUMATIQUE", "HYDRAULIQUE", "CLIMATISATION"]}
    ]}',
    'FAVORABLE',
    'Dommage consécutif à un accident — prise en charge au titre de la garantie collision, quelle que soit la pièce concernée.',
    2, true, 0, 'Expert assurance (audit V11)'
);

-- B2. Exclusion d'usure GÉNÉRALE (filet de sécurité « consommables »).
--   Lacune live : USURE+TRANSMISSION (et vitrage, climatisation, hydraulique,
--   AUTRE) → ETUDE faute de règle. Or l'usure normale n'est jamais couverte.
--   Priorité 1 (faible) : toute exclusion spécifique d'usure et tout geste
--   commercial VIP (priorité > 1) restent prioritaires. VIP exclu volontairement.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Exclusion usure générale (consommables)',
    'Règle générale : l''usure normale liée à l''utilisation du véhicule est exclue de la garantie pièces, sauf geste commercial VIP. Filet de sécurité couvrant les pièces sans règle d''usure spécifique.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"},
        {"field": "categorieClient", "op": "NOT_IN", "value": ["VIP"]}
    ]}',
    'DEFAVORABLE',
    'Usure normale liée à l''utilisation du véhicule — non couverte par la garantie pièces.',
    1, true, -8, 'Expert assurance (audit V11)'
);

-- B3. Bonus défaut de fabrication sur composant électrique/électronique récent.
--   Justification métier : un vice de fabrication électronique relève souvent
--   d'un rappel constructeur ; on renforce le score favorable (≤5 ans).
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Garantie défaut fabrication électronique (rappel constructeur)',
    'Vice de fabrication d''un composant électrique ou électronique sur véhicule récent (≤5 ans) — relève typiquement d''un rappel ou d''une garantie constructeur étendue.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "DEFAUT_FABRICATION"},
        {"field": "typePiece", "op": "IN", "value": ["ELECTRIQUE", "ELECTRONIQUE"]},
        {"field": "ageVehiculeAns", "op": "LTE", "value": 5}
    ]}',
    'FAVORABLE',
    'Vice de fabrication électronique sur véhicule récent — prise en charge renforcée au titre de la garantie constructeur / rappel.',
    6, true, 10, 'Expert assurance (audit V11)'
);


-- ────────────────────────────────────────────────────────────────────────────
-- PARTIE C — MISES À L'ÉTUDE MANQUANTES (clarté du motif)
-- ────────────────────────────────────────────────────────────────────────────

-- C1. Panne du système de freinage (hors usure) — sécurité, expertise requise.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne système de freinage',
    'Défaillance du système de freinage signalée comme une panne (et non une usure) — organe de sécurité critique nécessitant une expertise pour distinguer le défaut de la vétusté.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "typePiece", "op": "EQ", "value": "FREINAGE"}
    ]}',
    'ETUDE',
    'Panne d''un organe de sécurité (freinage) — expertise technique obligatoire avant décision.',
    3, true, 0, 'Expert assurance (audit V11)'
);

-- C2. Panne de suspension — distinguer défaut et usure, expertise requise.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne suspension',
    'Panne d''un élément de suspension — expertise requise pour distinguer un défaut prématuré d''une usure normale (amortisseurs, ressorts, triangles).',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "typePiece", "op": "EQ", "value": "SUSPENSION"}
    ]}',
    'ETUDE',
    'Panne de suspension — expertise requise pour qualifier l''origine (défaut vs usure).',
    3, true, 0, 'Expert assurance (audit V11)'
);

-- C3. Sinistre non catégorisé (AUTRE) — orienter explicitement vers l'étude.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude sinistre non catégorisé',
    'Sinistre de type AUTRE / non catégorisé — une qualification manuelle par l''opérateur est requise avant toute décision automatique.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "AUTRE"}
    ]}',
    'ETUDE',
    'Sinistre non catégorisé — qualification manuelle requise avant décision.',
    1, true, 0, 'Expert assurance (audit V11)'
);
