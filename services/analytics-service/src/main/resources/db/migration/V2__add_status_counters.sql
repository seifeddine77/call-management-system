-- V2: Ajout des compteurs par statut + compteur de résolutions pour le calcul du temps moyen
-- Ces colonnes permettent un suivi précis par statut au lieu d'estimations dérivées

ALTER TABLE daily_stats ADD COLUMN tickets_nouveau INT DEFAULT 0;
ALTER TABLE daily_stats ADD COLUMN tickets_en_cours INT DEFAULT 0;
ALTER TABLE daily_stats ADD COLUMN tickets_ferme INT DEFAULT 0;
ALTER TABLE daily_stats ADD COLUMN resolved_count_today INT DEFAULT 0;
ALTER TABLE daily_stats ADD COLUMN total_resolution_minutes DECIMAL(12,2) DEFAULT 0;
