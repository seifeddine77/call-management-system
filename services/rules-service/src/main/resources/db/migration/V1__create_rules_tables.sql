-- ============================================
-- V1 — Création des tables du moteur de règles
-- CDC Module 4 : Gestion des Règles d'Assurance
-- ============================================

-- Table principale des règles d'assurance
CREATE TABLE rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom VARCHAR(200) NOT NULL,
    description TEXT,
    -- Conditions stockées en JSONB (flexible, sans code)
    -- Format: {"operator":"AND","conditions":[{"field":"typePiece","op":"EQ","value":"MECANIQUE"},...]
    conditions JSONB NOT NULL,
    -- Action résultante de la règle
    action VARCHAR(15) NOT NULL CHECK (action IN ('FAVORABLE', 'DEFAVORABLE', 'ETUDE')),
    action_motif TEXT NOT NULL,
    -- Priorité de la règle (1-10, 10 = plus haute)
    priorite SMALLINT DEFAULT 5 CHECK (priorite BETWEEN 1 AND 10),
    actif BOOLEAN DEFAULT true,
    version SMALLINT DEFAULT 1,
    -- Audit
    created_by UUID,
    created_by_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Historique des versions (CDC 4.D — Versioning des règles)
CREATE TABLE rule_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
    conditions JSONB NOT NULL,
    action VARCHAR(15),
    action_motif TEXT,
    priorite SMALLINT,
    version SMALLINT NOT NULL,
    changed_by UUID,
    changed_by_name VARCHAR(100),
    changed_at TIMESTAMP DEFAULT NOW()
);

-- Index pour les requêtes courantes
CREATE INDEX idx_rules_actif ON rules(actif);
CREATE INDEX idx_rules_priorite ON rules(priorite DESC);
CREATE INDEX idx_rule_versions_rule_id ON rule_versions(rule_id);
CREATE INDEX idx_rules_conditions ON rules USING GIN (conditions);
