-- V2 : RGPD — Ajout du consentement d'enregistrement
-- DEFAULT false pour ne pas casser les tickets existants
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS consentement_enregistrement BOOLEAN DEFAULT false;
