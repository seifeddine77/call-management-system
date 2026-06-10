#!/bin/sh
set -e

SESSION_DIR=/app/.wwebjs_auth

# Le conteneur démarre en root uniquement pour réparer la propriété du volume
# de session : un volume créé avant le durcissement non-root (ou restauré
# depuis une sauvegarde) contient des fichiers root → EACCES sur le nettoyage
# des SingletonLock dans index.js → crash-loop Chromium ("profile appears to
# be in use by another Chromium process"). On bascule ensuite sur l'utilisateur
# non privilégié `app` via su-exec (pas de processus parent résiduel en root).
if [ "$(id -u)" = "0" ]; then
    if [ -n "$(find "$SESSION_DIR" ! -user app -print -quit 2>/dev/null)" ]; then
        echo "🔧 Fixing ownership of $SESSION_DIR (root-owned files detected)"
        chown -R app:app "$SESSION_DIR"
    fi
    exec su-exec app "$@"
fi

# Déjà non-root (ex: directive `user:` dans docker-compose) — exécuter tel quel.
exec "$@"
