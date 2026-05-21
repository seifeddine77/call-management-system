-- ============================================
-- V7 — Règles additionnelles d'assurance auto / garantie mécanique
-- Ajoute 4 règles conformes aux exclusions standard du marché
-- et aux dérogations pour les clients de valeur (VIP/Premium)
-- ============================================

-- 10. Exclusion collision pièce mécanique (Exclusion standard des garanties mécaniques)
-- Priorité 9 (Tranchant, juste sous Défaut de fabrication)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion collision pièce mécanique',
    'Refus de prise en charge sous garantie mécanique pour tout dommage mécanique faisant suite à un accident (les collisions relèvent de l''assurance tous risques)',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "ACCIDENT"}
    ]}',
    'DEFAVORABLE',
    'Le dommage mécanique résulte d''un accident. Les garanties mécaniques excluent les collisions, qui doivent être déclarées au titre de la couverture assurance automobile accident/collision.',
    9, true, 'SYSTEM'
);

-- 11. Geste commercial VIP pour usure modérée
-- Priorité 8 (Haute priorité pour les clients de valeur)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Geste commercial VIP usure',
    'Prise en charge exceptionnelle (dérogation commercial) de l''usure normale pour les clients VIP sous réserve d''un kilométrage modéré (< 120 000 km)',
    '{"operator": "AND", "conditions": [
        {"field": "categorieClient", "op": "EQ", "value": "VIP"},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"},
        {"field": "kilometrage", "op": "LTE", "value": 120000}
    ]}',
    'FAVORABLE',
    'Geste commercial exceptionnel accordé au client VIP : prise en charge de l''usure normale sur véhicule à kilométrage raisonnable.',
    8, true, 'SYSTEM'
);

-- 12. Exclusion usure flotte professionnelle
-- Priorité 7
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion usure flotte professionnelle',
    'Refus systématique de prise en charge pour usure normale sur les véhicules de flottes professionnelles (utilisation intensive par nature exclue)',
    '{"operator": "AND", "conditions": [
        {"field": "categorieClient", "op": "EQ", "value": "PROFESSIONNEL"},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"}
    ]}',
    'DEFAVORABLE',
    'Exclusion de l''usure pour les véhicules de flotte professionnelle en raison de l''usage intensif et des clauses spécifiques d''exclusion de garantie.',
    7, true, 'SYSTEM'
);

-- 13. Étude sinistre non spécifié Premium/VIP
-- Priorité 7
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Etude sinistre indéterminé VIP/Premium',
    'Mise en étude manuelle de toute demande avec sinistre non répertorié (AUTRE) pour les clients premium ou VIP',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "AUTRE"},
        {"field": "categorieClient", "op": "IN", "value": ["PREMIUM", "VIP"]}
    ]}',
    'ETUDE',
    'Sinistre non spécifié pour un client à haut niveau de service. Une analyse manuelle de l''admissibilité est requise.',
    7, true, 'SYSTEM'
);
