-- ============================================
-- V5 — Règles complémentaires (couverture complète)
-- Ajoute 3 règles pour couvrir tous les types
-- de sinistre et de pièce manquants
-- ============================================

-- 7. Vol → ETUDE (enquête + rapport de police obligatoire)
-- Priorité 8 (sous la règle age>15 prio 9 pour éviter les conflits)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Enquête vol systématique',
    'Tout sinistre de type vol nécessite une enquête et un rapport de police avant décision',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "VOL"}
    ]}',
    'ETUDE',
    'Sinistre de type vol déclaré. Une enquête complémentaire et un rapport de police sont requis avant toute décision de prise en charge.',
    8, true, 'SYSTEM'
);

-- 8. Électronique récent → FAVORABLE (calculateurs, capteurs, modules ECU)
-- Les composants électroniques ont une garantie similaire aux électriques (3-5 ans)
-- Priorité 6 (standard, sous les garanties mécaniques/électriques)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Garantie électronique véhicule récent',
    'Prise en charge des composants électroniques (calculateurs, capteurs, modules ECU) pour véhicules de moins de 5 ans en cas de panne ou défaut',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "ELECTRONIQUE"},
        {"field": "ageVehiculeAns", "op": "LT", "value": 5},
        {"field": "typeSinistre", "op": "IN", "value": ["PANNE", "DEFAUT_FABRICATION"]}
    ]}',
    'FAVORABLE',
    'Composant électronique sous garantie constructeur. Véhicule récent avec sinistre couvert par la police standard.',
    6, true, 'SYSTEM'
);

-- 9. Pneumatique + usure → DEFAVORABLE (pièce d'usure par définition)
-- Les pneus, jantes et valves sont toujours considérés comme pièces d'usure
-- Priorité 7 (au-dessus de l'exclusion usure+km élevé car applicable à tout km)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion pneumatique usure',
    'Les pièces pneumatiques (pneus, jantes, valves) sont des pièces d''usure non couvertes par la garantie, quel que soit le kilométrage',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "PNEUMATIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"}
    ]}',
    'DEFAVORABLE',
    'Pièce pneumatique classée en usure normale. Les pneus et composants pneumatiques sont des consommables non couverts par la garantie.',
    7, true, 'SYSTEM'
);
