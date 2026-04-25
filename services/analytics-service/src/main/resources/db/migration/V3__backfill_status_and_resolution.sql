-- V3: Backfill des compteurs de statut et de résolution pour les données historiques/seed
-- Les colonnes V2 (tickets_nouveau, tickets_en_cours, tickets_ferme, resolved_count_today,
-- total_resolution_minutes) étaient à 0 pour toutes les lignes existantes.

-- 1. Backfill resolved_count_today à partir de tickets_resolus existants
UPDATE daily_stats
SET resolved_count_today = tickets_resolus
WHERE resolved_count_today = 0 AND tickets_resolus > 0;

-- 2. Backfill total_resolution_minutes à partir de temps_moyen_minutes * tickets_resolus
-- (reconstruit le total à partir de la moyenne existante)
UPDATE daily_stats
SET total_resolution_minutes = temps_moyen_minutes * tickets_resolus
WHERE total_resolution_minutes = 0 AND temps_moyen_minutes IS NOT NULL AND temps_moyen_minutes > 0;

-- 3. Estimer tickets_nouveau pour les données historiques
-- Pour le seed data : tickets non résolus = total - résolus (approximation conservative)
UPDATE daily_stats
SET tickets_nouveau = GREATEST(0, total_tickets - tickets_resolus - tickets_en_cours - tickets_ferme)
WHERE tickets_nouveau = 0 AND total_tickets > 0;
