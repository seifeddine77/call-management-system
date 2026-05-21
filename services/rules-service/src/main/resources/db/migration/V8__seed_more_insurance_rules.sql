-- ============================================
-- V8 — Règles d'assurance auto additionnelles (Expertise métier de couverture)
-- Ajoute 5 nouvelles règles d'exclusion, de mise en étude et de geste commercial.
-- ============================================

-- 14. Exclusion usure précoce sur kilométrage anormalement élevé
-- Priorité 7 (Moyenne-Haute)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion usure précoce km anormal',
    'Si le véhicule a moins de 3 ans mais a déjà plus de 150 000 km, les pannes d''usure normale sont exclues (suspicion d''usage commercial intensif)',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "USURE"},
        {"field": "ageVehiculeAns", "op": "LTE", "value": 3},
        {"field": "kilometrage", "op": "GT", "value": 150000}
    ]}',
    'DEFAVORABLE',
    'Kilométrage anormalement élevé pour l''âge du véhicule (usage intensif ou suspicion de fraude). L''usure accélérée est exclue.',
    7, true, 'SYSTEM'
);

-- 15. Geste commercial bris de glace Premium
-- Priorité 6 (Moyenne)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Geste commercial bris de glace Premium',
    'Prise en charge intégrale sans franchise pour bris de glace de type vitrage pour les clients Premium ou VIP',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "BRIS_GLACE"},
        {"field": "typePiece", "op": "EQ", "value": "VITRAGE"},
        {"field": "categorieClient", "op": "IN", "value": ["PREMIUM", "VIP"]}
    ]}',
    'FAVORABLE',
    'Geste commercial : exonération totale de franchise bris de glace accordée aux profils de clients Premium et VIP.',
    6, true, 'SYSTEM'
);

-- 16. Étude incendie véhicule ancien
-- Priorité 8 (Haute)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Etude incendie vehicule ancien',
    'Tout incendie sur un véhicule âgé de plus de 10 ans nécessite une expertise approfondie pour exclure la négligence ou la vétusté',
    '{"operator": "AND", "conditions": [
        {"field": "typeSinistre", "op": "EQ", "value": "INCENDIE"},
        {"field": "ageVehiculeAns", "op": "GT", "value": 10}
    ]}',
    'ETUDE',
    'Sinistre incendie sur véhicule ancien (>10 ans) nécessitant une expertise technique obligatoire pour évaluer la cause et la vétusté.',
    8, true, 'SYSTEM'
);

-- 17. Exclusion panne électronique inondation / catastrophe naturelle
-- Priorité 9 (Haute)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Exclusion panne electronique inondation',
    'Les pannes électroniques consécutives à une catastrophe naturelle (inondations) sont exclues de la garantie mécanique classique',
    '{"operator": "AND", "conditions": [
        {"field": "typePiece", "op": "EQ", "value": "ELECTRONIQUE"},
        {"field": "typeSinistre", "op": "EQ", "value": "CATASTROPHE_NATURELLE"}
    ]}',
    'DEFAVORABLE',
    'Les pannes électroniques consécutives à des événements climatiques ou catastrophes naturelles sont exclues de la garantie panne standard et relèvent de la couverture spécifique catastrophes naturelles.',
    9, true, 'SYSTEM'
);

-- 18. Geste commercial Nouveau client Panne mécanique récente
-- Priorité 5 (Moyenne)
INSERT INTO rules (nom, description, conditions, action, action_motif, priorite, actif, created_by_name) VALUES (
    'Geste commercial Nouveau client Panne',
    'Prise en charge exceptionnelle pour les nouveaux clients en cas de panne mécanique sur un véhicule récent de moins de 4 ans et de moins de 80 000 km',
    '{"operator": "AND", "conditions": [
        {"field": "categorieClient", "op": "EQ", "value": "NOUVEAU"},
        {"field": "typeSinistre", "op": "EQ", "value": "PANNE"},
        {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
        {"field": "ageVehiculeAns", "op": "LT", "value": 4},
        {"field": "kilometrage", "op": "LTE", "value": 80000}
    ]}',
    'FAVORABLE',
    'Geste de bienvenue accordé à un nouveau client pour la prise en charge d''une panne mécanique sur un véhicule récent.',
    5, true, 'SYSTEM'
);
