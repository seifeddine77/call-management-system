# ---- Stage 1: Build ----
FROM node:22-alpine AS build

WORKDIR /app

# Copier les fichiers de dépendances
COPY package.json package-lock.json ./
RUN npm ci

# Copier le code source
COPY . .

# Build de production
ARG VITE_API_URL=
ARG VITE_WS_URL=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL

RUN npm run build

# ---- Stage 2: Serve ----
FROM nginx:alpine

# Copier le build
COPY --from=build /app/dist /usr/share/nginx/html

# Config Nginx pour SPA (React Router)
# resolver 127.0.0.11 = DNS interne Docker, évite le cache d'IP stale après recreation de containers
COPY nginx.conf /etc/nginx/conf.d/default.conf


EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
