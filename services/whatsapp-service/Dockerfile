FROM node:22-alpine

# Installer Chromium pour whatsapp-web.js (puppeteer)
RUN apk add --no-cache \
    ca-certificates \
    chromium \
    freetype \
    harfbuzz \
    nss \
    ttf-freefont

# Configurer Puppeteer pour utiliser Chromium installé
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts

COPY index.js .

# Créer le dossier pour la session WhatsApp persistée + utilisateur non-root.
# Chromium est déjà lancé avec --no-sandbox --disable-setuid-sandbox (index.js),
# l'exécution en non-root est donc supportée. (Durcissement CIS Docker / OWASP)
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/.wwebjs_auth \
    && chown -R app:app /app
USER app

CMD ["node", "index.js"]
