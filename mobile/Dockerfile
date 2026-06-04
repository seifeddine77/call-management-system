# ============================================
# AutoAssist Mobile — Flutter Web Production Build
# Multi-stage: Flutter SDK → Build → Nginx Serve
# ============================================

# ---- Stage 1: Flutter Web Build ----
FROM ghcr.io/cirruslabs/flutter:3.24.5 AS build

WORKDIR /app

# Copy pubspec first for dependency caching
COPY pubspec.yaml pubspec.lock ./

# Get dependencies (cached layer)
RUN flutter pub get && flutter pub upgrade google_fonts

# Copy the rest of the source code
COPY . .

# Build Flutter Web release (CanvasKit renderer for best quality)
RUN flutter build web --release --base-href "/"

# ---- Stage 2: Serve with Nginx ----
FROM nginx:alpine

# Copy Flutter web build output
COPY --from=build /app/build/web /usr/share/nginx/html

# Copy Nginx configuration
COPY default.conf /etc/nginx/conf.d/default.conf

EXPOSE 5555

CMD ["nginx", "-g", "daemon off;"]
