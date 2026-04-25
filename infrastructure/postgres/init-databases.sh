#!/bin/bash
set -e

# ============================================
# Script d'initialisation des bases de données
# Exécuté automatiquement au premier démarrage de PostgreSQL
# ============================================

echo "=== Initialisation des bases de données ==="

DATABASES=(
  "auth_db"
  "users_db"
  "tickets_db"
  "rules_db"
  "decisions_db"
  "notif_db"
  "analytics_db"
  "ai_db"
  "n8n_db"
)

for DB_NAME in "${DATABASES[@]}"; do
  echo "Vérification de la base : $DB_NAME"
  
  # Vérifier si la base existe déjà
  DB_EXISTS=$(psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")
  
  if [ "$DB_EXISTS" != "1" ]; then
    echo "  → Création de $DB_NAME..."
    psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $DB_NAME OWNER $POSTGRES_USER;"
  else
    echo "  → $DB_NAME existe déjà, skip."
  fi

  # Activer l'extension uuid-ossp dans chaque base
  echo "  → Activation uuid-ossp dans $DB_NAME..."
  psql -U "$POSTGRES_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
done

# Activer pgvector dans ai_db (pour la feature RAG future)
echo "Activation pgvector dans ai_db..."
psql -U "$POSTGRES_USER" -d "ai_db" -c "CREATE EXTENSION IF NOT EXISTS vector;" 2>/dev/null || echo "  → pgvector non disponible (sera installé plus tard si nécessaire)"

echo "=== Initialisation terminée ==="
