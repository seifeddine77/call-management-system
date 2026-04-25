FROM node:22-alpine

# Installer Chromium pour whatsapp-web.js (puppeteer)
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont

# Configurer Puppeteer pour utiliser Chromium installé
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY index.js .

# Créer le dossier pour la session WhatsApp persistée
RUN mkdir -p /app/.wwebjs_auth

CMD ["node", "index.js"]
