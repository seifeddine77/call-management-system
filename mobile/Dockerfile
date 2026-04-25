# ============================================
# AutoAssist Mobile — Flutter Web Production Build
# Multi-stage: Flutter SDK → Build → Nginx Serve
# ============================================

# ---- Stage 1: Flutter Web Build ----
FROM ghcr.io/cirruslabs/flutter:stable AS build

WORKDIR /app

# Copy pubspec first for dependency caching
COPY pubspec.yaml pubspec.lock ./

# Get dependencies (cached layer)
RUN flutter pub get

# Copy the rest of the source code
COPY . .

# Build Flutter Web release (CanvasKit renderer for best quality)
RUN flutter build web --release --base-href "/"

# ---- Stage 2: Serve with Nginx ----
FROM nginx:alpine

# Copy Flutter web build output
COPY --from=build /app/build/web /usr/share/nginx/html

# Custom Nginx config for SPA routing + API proxy
RUN cat > /etc/nginx/conf.d/default.conf << 'EOF'
server {
    listen 5555;
    root /usr/share/nginx/html;
    index index.html;

    # Gzip compression for Flutter assets
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript application/wasm;
    gzip_min_length 256;
    gzip_vary on;

    # Cache static assets aggressively
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|wasm)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Flutter SPA fallback — all routes serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy to gateway (inside Docker network)
    location /api {
        proxy_pass http://api-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy
    location /ws {
        proxy_pass http://api-gateway:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
EOF

EXPOSE 5555

CMD ["nginx", "-g", "daemon off;"]
