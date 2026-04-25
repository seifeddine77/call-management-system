-- =============================================
-- V3: Ajout colonne photos_paths (CDC Module 5)
-- Stocke les chemins des photos sinistre en JSONB
-- =============================================
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS photos_paths JSONB;
