-- ============================================================================
-- V12 — Complétion de couverture (expertise assurance)
--
-- Suite au balayage systématique de la matrice (type de sinistre × type de pièce)
-- sur le moteur live, fermeture des cas encore non couverts qui tombaient en
-- "aucune règle → ETUDE 30 par défaut", lorsque ces cas ont un sens métier.
-- Rappel sémantique : priorité HAUTE = prioritaire ; une règle ETUDE = veto.
-- ============================================================================

-- 1. ACCIDENT — règle générale de couverture.
--    Tout dommage consécutif à un accident est couvert (garantie collision),
--    quelle que soit la pièce — y compris une pièce non catégorisée (AUTRE).
--    Priorité 1 (faible) : les mises à l'étude (véhicule ancien) restent un veto,
--    et l'exclusion > 15 ans (priorité 10, DEFAVORABLE) reste prioritaire.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Prise en charge accident (règle générale)',
    'Tout dommage matériel consécutif à un accident relève de la garantie collision, quelle que soit la pièce concernée (filet de sécurité couvrant les pièces non catégorisées).',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "ACCIDENT"}
    ]}',
    'FAVORABLE',
    'Dommage consécutif à un accident — prise en charge au titre de la garantie collision.',
    1, true, 0, 'Expert assurance (audit V12)'
);

-- 2. BRIS_GLACE — règle générale de couverture.
--    Le bris de glace est un péril couvert en soi ; il concerne par nature le
--    vitrage. On garantit la prise en charge même si la pièce n'est pas
--    explicitement renseignée comme VITRAGE.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Prise en charge bris de glace (règle générale)',
    'Le bris de glace est un péril couvert (pare-brise, lunette, vitres). Prise en charge garantie même lorsque la pièce n''est pas renseignée comme VITRAGE.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "BRIS_GLACE"}
    ]}',
    'FAVORABLE',
    'Bris de glace — péril couvert, prise en charge du vitrage.',
    1, true, 0, 'Expert assurance (audit V12)'
);

-- 3. PANNE électrique hors garantie constructeur (>5 ans) — mise à l'étude.
--    La "Garantie panne électrique récent" ne couvre que ≤5 ans ; au-delà,
--    une panne électrique nécessite une expertise (défaut vs vétusté faisceau).
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne électrique véhicule hors garantie',
    'Panne d''un composant électrique sur véhicule de plus de 5 ans — hors garantie constructeur, expertise requise pour distinguer le défaut de la vétusté du faisceau.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "typePiece", "op": "EQ", "value": "ELECTRIQUE"},
        {"field": "ageVehiculeAns", "op": "GT", "value": 5}
    ]}',
    'ETUDE',
    'Panne électrique hors garantie constructeur (>5 ans) — expertise technique requise.',
    3, true, 0, 'Expert assurance (audit V12)'
);

-- 4. PANNE sur pièce non catégorisée (AUTRE) — qualification requise.
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne pièce non catégorisée',
    'Panne signalée sur une pièce non catégorisée — qualification manuelle de l''organe concerné requise avant décision.',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "typePiece", "op": "EQ", "value": "AUTRE"}
    ]}',
    'ETUDE',
    'Panne sur pièce non catégorisée — qualification manuelle requise.',
    1, true, 0, 'Expert assurance (audit V12)'
);
