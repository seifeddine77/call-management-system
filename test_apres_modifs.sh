#!/usr/bin/env bash
# =============================================================================
# Test de validation après les correctifs (non-root, limites, CORS, erreurs)
# À lancer sur TA machine avec Docker Desktop démarré.
# Windows : ouvrir Git Bash ou WSL, puis :  bash test_apres_modifs.sh
# =============================================================================
set -uo pipefail
cd "$(dirname "$0")"

PASS=0; FAIL=0
ok()   { echo "  [PASS] $1"; PASS=$((PASS+1)); }
ko()   { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
info() { echo ">> $1"; }

# --- 0. Pré-requis -----------------------------------------------------------
info "0. Pré-requis"
if ! command -v docker >/dev/null 2>&1; then echo "Docker introuvable. Démarre Docker Desktop."; exit 1; fi
docker info >/dev/null 2>&1 && ok "Docker daemon actif" || { ko "Docker daemon inactif"; exit 1; }

# --- 1. Validation de la config compose --------------------------------------
info "1. docker compose config"
docker compose config >/dev/null 2>err.log && ok "compose config valide" || { ko "compose config invalide"; cat err.log; }

# --- 2. Build des images modifiées -------------------------------------------
info "2. Build (ai-service, whatsapp-service, api-gateway, notification, analytics)"
if docker compose build ai-service whatsapp-service api-gateway notification-service analytics-service; then
  ok "Build des images modifiées réussi"
else
  ko "Échec de build — voir logs ci-dessus"; exit 1
fi

# --- 3. Démarrage ------------------------------------------------------------
info "3. docker compose up -d"
docker compose up -d && ok "Stack démarrée" || ko "Échec du démarrage"

info "   Attente de la stabilisation (120 s pour le warmup ai-service)..."
sleep 120

# --- 4. État des conteneurs --------------------------------------------------
info "4. État des conteneurs"
docker compose ps
UNHEALTHY=$(docker compose ps --format '{{.Name}} {{.Health}}' 2>/dev/null | grep -i unhealthy || true)
[ -z "$UNHEALTHY" ] && ok "Aucun conteneur unhealthy" || ko "Conteneurs unhealthy: $UNHEALTHY"

# --- 5. CORRECTIF 1 & 2 : exécution non-root ---------------------------------
info "5. Vérification non-root (correctifs 1 & 2)"
AIU=$(docker compose exec -T ai-service whoami 2>/dev/null | tr -d '\r')
[ "$AIU" = "app" ] && ok "ai-service tourne en non-root ($AIU)" || ko "ai-service user = '$AIU' (attendu: app)"
WAU=$(docker compose exec -T whatsapp-service whoami 2>/dev/null | tr -d '\r')
[ "$WAU" = "app" ] && ok "whatsapp-service tourne en non-root ($WAU)" || ko "whatsapp-service user = '$WAU' (attendu: app)"

# --- 6. ai-service : modèles bien en cache (pas de re-téléchargement) ---------
info "6. ai-service : cache modèles sous le home non-root"
docker compose exec -T ai-service sh -c 'ls /home/app/.cache/whisper >/dev/null 2>&1' \
  && ok "Cache Whisper présent sous /home/app/.cache" \
  || ko "Cache Whisper absent — risque de re-téléchargement (vérifier le build)"

# --- 7. Santé des endpoints --------------------------------------------------
info "7. Santé des endpoints"
curl -fsS http://localhost:8000/health    >/dev/null 2>&1 && ok "ai-service /health OK"        || ko "ai-service /health KO"
curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1 && ok "api-gateway /health OK" || ko "api-gateway /health KO"
curl -fsS http://localhost:8761/actuator/health >/dev/null 2>&1 && ok "eureka /health OK"       || ko "eureka /health KO"

# --- 8. CORRECTIF 5 : CORS (préflight depuis une origine autorisée) ----------
info "8. CORS (correctif 5)"
ACAO=$(curl -s -o /dev/null -D - -X OPTIONS http://localhost:8080/api/auth/login \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" 2>/dev/null | grep -i 'access-control-allow-origin' || true)
[ -n "$ACAO" ] && ok "Préflight CORS autorisé pour localhost:3000" || ko "Préflight CORS sans en-tête Allow-Origin"

# --- 9. CORRECTIF 3 : limites de ressources appliquées -----------------------
info "9. Limites de ressources (correctif 3)"
AILIM=$(docker inspect callmgmt-ai --format '{{.HostConfig.Memory}}' 2>/dev/null)
[ "${AILIM:-0}" != "0" ] && ok "ai-service: limite mémoire appliquée ($AILIM octets)" || ko "ai-service: pas de limite mémoire"
WALIM=$(docker inspect callmgmt-whatsapp --format '{{.HostConfig.Memory}}' 2>/dev/null)
[ "${WALIM:-0}" != "0" ] && ok "whatsapp-service: limite mémoire appliquée ($WALIM octets)" || ko "whatsapp-service: pas de limite mémoire"

# --- 10. Consommation ressources --------------------------------------------
info "10. Consommation (instantané)"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' 2>/dev/null | head -20

# --- Bilan -------------------------------------------------------------------
echo
echo "================= BILAN : $PASS PASS / $FAIL FAIL ================="
echo "Tests fonctionnels manuels recommandés :"
echo "  - Connexion front (http://localhost:3000) + une action authentifiée"
echo "  - Upload audio -> transcription (valide l'écriture dans /var/audio en non-root)"
echo "  - Appairage WhatsApp puis : docker compose restart whatsapp-service"
echo "    -> la session doit persister (volume whatsapp_session)"
rm -f err.log
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
