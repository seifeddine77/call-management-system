-- ============================================
-- V6 — Ajout des colonnes de traçabilité d'audit dans rule_versions
-- Permet de conserver le nom et la description d'origine lors des modifications.
-- ============================================

ALTER TABLE rule_versions ADD COLUMN nom VARCHAR(200);
ALTER TABLE rule_versions ADD COLUMN description TEXT;
