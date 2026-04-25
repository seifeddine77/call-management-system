-- V3: Migrer local_users : renommer full_name en nom + prenom
ALTER TABLE local_users ADD COLUMN IF NOT EXISTS nom VARCHAR(50);
ALTER TABLE local_users ADD COLUMN IF NOT EXISTS prenom VARCHAR(50);

-- Migrer les données existantes
UPDATE local_users SET
    prenom = SPLIT_PART(full_name, ' ', 1),
    nom = CASE
        WHEN POSITION(' ' IN full_name) > 0
        THEN SUBSTRING(full_name FROM POSITION(' ' IN full_name) + 1)
        ELSE ''
    END
WHERE full_name IS NOT NULL AND nom IS NULL;

-- Rendre les colonnes non-null
ALTER TABLE local_users ALTER COLUMN nom SET NOT NULL;
ALTER TABLE local_users ALTER COLUMN prenom SET NOT NULL;

-- Supprimer l'ancienne colonne
ALTER TABLE local_users DROP COLUMN IF EXISTS full_name;
