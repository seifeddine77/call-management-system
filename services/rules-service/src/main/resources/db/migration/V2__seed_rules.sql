-- ============================================
-- V2 — Données de démonstration (règles d'assurance)
-- 6 règles réalistes pour la soutenance
-- ============================================

-- 1. Pièce mécanique récente → FAVORABLE
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Garantie mécanique véhicule récent',
    'Prise en charge des pièces mécaniques pour les véhicules de moins de 5 ans avec kilométrage < 100 000 km',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
        {"field": "ageVehiculeAns", "op": "LT", "value": 5},
        {"field": "kilometrage", "op": "LTE", "value": 100000}
    ]}',
    'FAVORABLE',
    'Pièce mécanique sous garantie standard. Véhicule récent et kilométrage dans les limites de couverture.',
    8, true, 'SYSTEM'
);

-- 2. Défaut fabrication → FAVORABLE (haute priorité)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Défaut de fabrication toute pièce',
    'Prise en charge intégrale en cas de défaut de fabrication confirmé, quel que soit le type de pièce',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "DEFAUT_FABRICATION"}
    ]}',
    'FAVORABLE',
    'Défaut de fabrication avéré. Prise en charge à 100% conformément aux obligations légales du constructeur.',
    10, true, 'SYSTEM'
);

-- 3. Pièce électrique + véhicule récent → FAVORABLE
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Garantie électrique véhicule récent',
    'Prise en charge des pièces électriques pour véhicules de moins de 7 ans',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "ELECTRIQUE"},
        {"field": "ageVehiculeAns", "op": "LT", "value": 7},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "DEFAUT_FABRICATION"]}
    ]}',
    'FAVORABLE',
    'Composant électrique sous garantie étendue. Sinistre couvert par la police standard.',
    7, true, 'SYSTEM'
);

-- 4. Usure + km élevé → DEFAVORABLE
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion usure kilométrage élevé',
    'Refus de prise en charge pour usure normale sur véhicules à fort kilométrage',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"},
        {"field": "kilometrage", "op": "GT", "value": 150000}
    ]}',
    'DEFAVORABLE',
    'Usure normale constatée sur véhicule à kilométrage élevé. Non couvert par la garantie.',
    6, true, 'SYSTEM'
);

-- 5. Carrosserie + accident → ETUDE
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Étude carrosserie accident',
    'Les dommages de carrosserie suite à accident nécessitent une expertise avant décision',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "CARROSSERIE"},
        {"field": "typeSinistre", "op": "EQ", "value": "ACCIDENT"}
    ]}',
    'ETUDE',
    'Dommage de carrosserie suite à accident. Une expertise complémentaire est requise pour évaluer la prise en charge.',
    5, true, 'SYSTEM'
);

-- 6. Véhicule très ancien → DEFAVORABLE
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion véhicule ancien',
    'Refus de prise en charge pour véhicules de plus de 15 ans, toutes pièces confondues',
    '{"operator": "AND", "conditions": [
        {"field": "ageVehiculeAns", "op": "GT", "value": 15}
    ]}',
    'DEFAVORABLE',
    'Véhicule hors période de couverture. L''âge du véhicule dépasse la limite maximale de garantie (15 ans).',
    9, true, 'SYSTEM'
);
