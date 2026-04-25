-- ============================================
-- V4 — Définitions de champs dynamiques (CDC 4.B étendu)
-- Permet d'ajouter des champs de condition sans redéploiement.
-- category = 'FIELD_DEFINITION'
-- code = nom technique du champ (ex: typePiece)
-- label = libellé affiché dans l'éditeur de règles
-- description = type du champ + catégorie enum si applicable
--   Format: "number" | "string" | "enum:CATEGORY_NAME"
-- ============================================

INSERT INTO reference_items (category, code, label, description, display_order) VALUES
    ('FIELD_DEFINITION', 'typePiece',         'Type de pièce',           'enum:TYPE_PIECE',       1),
    ('FIELD_DEFINITION', 'ageVehiculeAns',    'Âge du véhicule (années)', 'number',               2),
    ('FIELD_DEFINITION', 'kilometrage',        'Kilométrage',             'number',                3),
    ('FIELD_DEFINITION', 'categorieClient',   'Catégorie client',         'enum:CATEGORIE_CLIENT', 4),
    ('FIELD_DEFINITION', 'typeSinistre',      'Type de sinistre',         'enum:TYPE_SINISTRE',    5),
    ('FIELD_DEFINITION', 'pieceReference',    'Référence pièce',          'string',                6),
    ('FIELD_DEFINITION', 'transcriptionText', 'Transcription audio',      'string',                7);
