-- ============================================
-- V3 — Listes de références dynamiques (CDC 4.B)
-- Paramétrage : pièces garanties, exclusions, etc.
-- ============================================

CREATE TABLE reference_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,       -- TYPE_PIECE, CATEGORIE_CLIENT, TYPE_SINISTRE
    code VARCHAR(100) NOT NULL,          -- MECANIQUE, STANDARD, PANNE...
    label VARCHAR(200) NOT NULL,         -- Mécanique, Standard, Panne...
    description TEXT,                    -- Description optionnelle
    display_order INT DEFAULT 0,         -- Ordre d'affichage
    actif BOOLEAN DEFAULT true,          -- Actif/inactif
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (category, code)
);

CREATE INDEX idx_ref_items_category ON reference_items(category);
CREATE INDEX idx_ref_items_actif ON reference_items(category, actif);

-- Seed : Types de pièces (pièces garanties)
INSERT INTO reference_items (category, code, label, description, display_order) VALUES
    ('TYPE_PIECE', 'MECANIQUE', 'Mécanique', 'Pièces du moteur, transmission, freins', 1),
    ('TYPE_PIECE', 'ELECTRIQUE', 'Électrique', 'Alternateur, démarreur, faisceaux', 2),
    ('TYPE_PIECE', 'CARROSSERIE', 'Carrosserie', 'Panneaux, pare-chocs, portes', 3),
    ('TYPE_PIECE', 'ELECTRONIQUE', 'Électronique', 'Calculateurs, capteurs, modules', 4),
    ('TYPE_PIECE', 'PNEUMATIQUE', 'Pneumatique', 'Pneus, jantes, valves', 5),
    ('TYPE_PIECE', 'AUTRE', 'Autre', 'Pièces non classifiées', 6);

-- Seed : Catégories client
INSERT INTO reference_items (category, code, label, description, display_order) VALUES
    ('CATEGORIE_CLIENT', 'STANDARD', 'Standard', 'Client particulier standard', 1),
    ('CATEGORIE_CLIENT', 'PREMIUM', 'Premium', 'Client avec contrat premium', 2),
    ('CATEGORIE_CLIENT', 'VIP', 'VIP', 'Client VIP — priorité haute', 3),
    ('CATEGORIE_CLIENT', 'PROFESSIONNEL', 'Professionnel', 'Entreprise ou flotte', 4);

-- Seed : Types de sinistre
INSERT INTO reference_items (category, code, label, description, display_order) VALUES
    ('TYPE_SINISTRE', 'PANNE', 'Panne', 'Panne mécanique ou électrique', 1),
    ('TYPE_SINISTRE', 'ACCIDENT', 'Accident', 'Dommages suite à un accident', 2),
    ('TYPE_SINISTRE', 'USURE', 'Usure', 'Usure naturelle des pièces', 3),
    ('TYPE_SINISTRE', 'DEFAUT_FABRICATION', 'Défaut de fabrication', 'Vice caché du constructeur', 4),
    ('TYPE_SINISTRE', 'VOL', 'Vol', 'Vol de pièces ou du véhicule', 5),
    ('TYPE_SINISTRE', 'AUTRE', 'Autre', 'Sinistre non classifié', 6);
