-- ============================================
-- V10 — Correction complète du moteur de règles,
--       résolution des conflits, nettoyage des doublons
--       et ajout des 12 nouvelles règles critiques.
-- ============================================

-- ────────────────────────────────────────────
-- ÉTAPE 1 : Nettoyage des doublons (Sûreté de production)
-- ────────────────────────────────────────────
DELETE FROM rules WHERE nom = 'Exclusion usure pneumatique';
DELETE FROM rules WHERE nom = 'Enquête vol systématique' AND priorite = 1;

-- ────────────────────────────────────────────
-- ÉTAPE 2 : Résolution du conflit de priorité
-- MECANIQUE + ACCIDENT
-- ────────────────────────────────────────────
-- On abaisse la priorité de l'exclusion collision à 8
-- pour permettre à la règle favorable de s'appliquer.
UPDATE rules SET priorite = 8
WHERE nom = 'Exclusion collision pièce mécanique';

-- ────────────────────────────────────────────
-- ÉTAPE 3 : Ajouter les reference_items manquants
-- ────────────────────────────────────────────
INSERT INTO reference_items (category, code, label, description, display_order)
VALUES
    ('TYPE_SINISTRE', 'BRIS_GLACE', 'Bris de glace', 'Bris de vitrage (pare-brise, lunette, vitres)', 7),
    ('TYPE_SINISTRE', 'INCENDIE', 'Incendie', 'Incendie du véhicule', 8),
    ('TYPE_SINISTRE', 'CATASTROPHE_NATURELLE', 'Catastrophe naturelle', 'Inondation, grêle, tempête', 9)
ON CONFLICT (category, code) DO NOTHING;

INSERT INTO reference_items (category, code, label, description, display_order)
VALUES
    ('TYPE_PIECE', 'VITRAGE', 'Vitrage', 'Pare-brise, lunette arrière, vitres latérales', 7)
ON CONFLICT (category, code) DO NOTHING;

INSERT INTO reference_items (category, code, label, description, display_order)
VALUES
    ('CATEGORIE_CLIENT', 'NOUVEAU', 'Nouveau client', 'Client récemment souscrit', 5)
ON CONFLICT (category, code) DO NOTHING;

-- ────────────────────────────────────────────
-- ÉTAPE 4 : Insertion des 12 nouvelles règles (R1 - R12)
-- ────────────────────────────────────────────

-- R1 : Exclusion usure mécanique véhicule standard
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Exclusion usure mécanique standard',
    'L''usure mécanique est exclue de la garantie pièces pour les clients non-VIP. Usure normale liée à l''utilisation du véhicule.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"},
        {"field": "categorieClient", "op": "NOT_IN", "value": ["VIP"]}
    ]}',
    'DEFAVORABLE',
    'L''usure mécanique est exclue pour les clients non-VIP (usure normale liée à l''utilisation).',
    5, true, -10, 'SYSTEM'
);

-- R2 : Étude panne mécanique véhicule intermédiaire (5-15 ans)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne mécanique véhicule 5-15 ans',
    'Véhicule hors garantie constructeur (>5 ans) mais dans la plage d''éligibilité. Expertise technique requise.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "ageVehiculeAns", "op": "GT", "value": 5},
        {"field": "ageVehiculeAns", "op": "LTE", "value": 15}
    ]}',
    'ETUDE',
    'Véhicule hors garantie constructeur (>5 ans) mais dans la plage d''éligibilité. Expertise technique requise pour déterminer si la panne relève d''un vice caché.',
    4, true, 0, 'SYSTEM'
);

-- R3 : Prise en charge électronique post-accident
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Réparation électronique post-accident',
    'Composant électronique endommagé suite à un accident — prise en charge standard au titre de la couverture accident.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "ELECTRONIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "ACCIDENT"}
    ]}',
    'FAVORABLE',
    'Composant électronique endommagé suite à un accident — prise en charge standard au titre de la couverture accident.',
    2, true, 5, 'SYSTEM'
);

-- R4 : Exclusion usure électrique/électronique
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Exclusion usure composants électriques et électroniques',
    'L''usure des composants électriques et électroniques est exclue de la couverture. Durée de vie normale du composant atteinte.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "IN", "value": ["ELECTRIQUE", "ELECTRONIQUE"]},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"}
    ]}',
    'DEFAVORABLE',
    'L''usure des composants électriques et électroniques est exclue de la couverture.',
    3, true, -8, 'SYSTEM'
);

-- R5 : Panne carrosserie (cas rare mais possible)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne carrosserie',
    'Panne signalée sur un élément de carrosserie — nature inhabituelle, expertise requise.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "CARROSSERIE"},
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"}
    ]}',
    'ETUDE',
    'Panne signalée sur un élément de carrosserie — nature inhabituelle, expertise requise (mécanisme d''ouverture, serrure, charnière).',
    3, true, 0, 'SYSTEM'
);

-- R6 : Exclusion usure carrosserie
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Exclusion usure carrosserie',
    'L''usure esthétique de la carrosserie (rouille, peinture, égratignures) n''est pas couverte par la garantie pièces.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "CARROSSERIE"},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"}
    ]}',
    'DEFAVORABLE',
    'L''usure esthétique de la carrosserie (rouille, peinture, égratignures) n''est pas couverte par la garantie pièces.',
    2, true, -5, 'SYSTEM'
);

-- R7 : Exclusion kilométrage élevé toutes pièces
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Exclusion kilométrage excessif général',
    'Kilométrage excessif (>250 000 km) — usure générale du véhicule incompatible avec une prise en charge garantie.',
    '{"operator": "AND", "conditions": [
        {"field": "kilometrage", "op": "GT", "value": 250000},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "USURE"]}
    ]}',
    'DEFAVORABLE',
    'Kilométrage excessif (>250 000 km) — usure générale du véhicule incompatible avec une prise en charge garantie.',
    8, true, -15, 'SYSTEM'
);

-- R8 : Garantie panne pneumatique (crevaison / éclatement)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Étude panne pneumatique',
    'Panne pneumatique (éclatement, crevaison) — expertise requise pour distinguer un défaut d''une usure normale.',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "PNEUMATIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"}
    ]}',
    'ETUDE',
    'Panne pneumatique (éclatement, crevaison) — expertise requise pour distinguer un défaut de fabrication d''une usure normale.',
    3, true, 0, 'SYSTEM'
);

-- R9 : Bonus fidélité client Premium
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Bonus fidélité client Premium',
    'Client Premium avec sinistre éligible — conditions préférentielles appliquées.',
    '{"operator": "AND", "conditions": [
        {"field": "categorieClient", "op": "EQ", "value": "PREMIUM"},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "DEFAUT_FABRICATION"]}
    ]}',
    'FAVORABLE',
    'Client Premium avec sinistre éligible — conditions préférentielles appliquées.',
    2, true, 5, 'SYSTEM'
);

-- R10 : Malus véhicule âgé (10-15 ans)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Malus véhicule âgé (10-15 ans)',
    'Véhicule âgé (10-15 ans) — garantie fortement réduite. Franchise élevée applicable.',
    '{"operator": "AND", "conditions": [
        {"field": "ageVehiculeAns", "op": "GT", "value": 10},
        {"field": "ageVehiculeAns", "op": "LTE", "value": 15}
    ]}',
    'DEFAVORABLE',
    'Véhicule âgé (10-15 ans) — garantie fortement réduite. Franchise élevée applicable.',
    3, true, -10, 'SYSTEM'
);

-- R11 : Bonus véhicule très récent (≤ 2 ans)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Bonus véhicule très récent',
    'Véhicule très récent (≤2 ans) — couverture intégrale garantie constructeur applicable sans franchise.',
    '{"operator": "AND", "conditions": [
        {"field": "ageVehiculeAns", "op": "LTE", "value": 2},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "DEFAUT_FABRICATION"]}
    ]}',
    'FAVORABLE',
    'Véhicule très récent (≤2 ans) — couverture intégrale garantie constructeur applicable sans franchise.',
    4, true, 15, 'SYSTEM'
);

-- R12 : Bonus kilométrage faible (≤ 30 000 km)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, score_impact, created_by_name) VALUES (
    'Bonus kilométrage faible',
    'Kilométrage très faible — panne probablement liée à un défaut et non à l''usure.',
    '{"operator": "AND", "conditions": [
        {"field": "kilometrage", "op": "LTE", "value": 30000},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "DEFAUT_FABRICATION"]}
    ]}',
    'FAVORABLE',
    'Kilométrage très faible — panne probablement liée à un défaut et non à l''usure.',
    3, true, 10, 'SYSTEM'
);
