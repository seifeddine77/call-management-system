FROM node:22-alpine

# Installer Chromium pour whatsapp-web.js (puppeteer)
# + su-exec : bascule root → app dans l'entrypoint (réparation des permissions
#   du volume de session avant de céder les privilèges)
RUN apk add --no-cache \
    ca-certificates \
    chromium \
    freetype \
    harfbuzz \
    nss \
    su-exec \
    ttf-freefont

# Configurer Puppeteer pour utiliser Chromium installé
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts

COPY index.js .
COPY entrypoint.sh /entrypoint.sh

# Créer le dossier pour la session WhatsApp persistée + utilisateur non-root.
# Chromium est déjà lancé avec --no-sandbox --disable-setuid-sandbox (index.js),
# l'exécution en non-root est donc supportée. (Durcissement CIS Docker / OWASP)
# sed : neutralise d'éventuels CRLF (build depuis un poste Windows).
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/.wwebjs_auth \
    && chown -R app:app /app \
    && sed -i 's/\r$//' /entrypoint.sh \
    && chmod +x /entrypoint.sh

# Pas de USER ici : l'entrypoint démarre en root pour réparer la propriété du
# volume whatsapp_session (fichiers root hérités d'un volume pré-durcissement)
# puis exécute node en tant que `app` via su-exec.
ENTRYPOINT ["/entrypoint.sh"]
CMD ["node", "index.js"]
