-- ============================================
-- V9 — Ajout du score_impact dynamique aux règles d'assurance
-- CDC Module 4 / Refactoring dynamique du scoring
-- ============================================

-- 1. Ajout de la colonne aux tables principales et d'historique
ALTER TABLE rules ADD COLUMN score_impact SMALLINT DEFAULT 0 NOT NULL;
ALTER TABLE rule_versions ADD COLUMN score_impact SMALLINT DEFAULT 0;

-- 2. Mise à jour des règles de "Geste Commercial" / Favorable (V9 additions)
UPDATE rules SET score_impact = 10 WHERE nom = 'Geste commercial VIP usure';
UPDATE rules SET score_impact = 8 WHERE nom = 'Geste commercial bris de glace Premium';
UPDATE rules SET score_impact = 15 WHERE nom = 'Geste commercial Nouveau client Panne';

-- 3. Mise à jour des règles d'"Exclusion" / Défavorable (V9 additions)
UPDATE rules SET score_impact = -15 WHERE nom = 'Exclusion collision pièce mécanique';
UPDATE rules SET score_impact = -10 WHERE nom = 'Exclusion usure flotte professionnelle';
UPDATE rules SET score_impact = -15 WHERE nom = 'Exclusion usure précoce km anormal';
UPDATE rules SET score_impact = -20 WHERE nom = 'Exclusion panne electronique inondation';

-- 4. Mise à jour des règles d'"Etude" (V9 additions)
UPDATE rules SET score_impact = -5 WHERE nom = 'Etude sinistre indéterminé VIP/Premium';
UPDATE rules SET score_impact = -10 WHERE nom = 'Etude incendie vehicule ancien';

-- 5. Mise à jour des règles héritées (V2 et V5) pour un scoring 100% cohérent
-- Favorable
UPDATE rules SET score_impact = 12 WHERE nom = 'Garantie mécanique véhicule récent';
UPDATE rules SET score_impact = 20 WHERE nom = 'Défaut de fabrication toute pièce';
UPDATE rules SET score_impact = 10 WHERE nom = 'Garantie électrique véhicule récent';
UPDATE rules SET score_impact = 10 WHERE nom = 'Garantie électronique véhicule récent';

-- Défavorable
UPDATE rules SET score_impact = -15 WHERE nom = 'Exclusion usure kilométrage élevé';
UPDATE rules SET score_impact = -25 WHERE nom = 'Exclusion véhicule ancien';
UPDATE rules SET score_impact = -15 WHERE nom = 'Exclusion pneumatique usure';

-- Étude
UPDATE rules SET score_impact = -5 WHERE nom = 'Étude carrosserie accident';
UPDATE rules SET score_impact = -8 WHERE nom = 'Enquête vol systématique';
