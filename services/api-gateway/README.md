# API Gateway

**Port :** `8080` · **Stack :** Spring Cloud Gateway (WebFlux) · **Base de données :** aucune

## Rôle
Point d'entrée unique de toute l'API. Route les requêtes `/api/**` vers les microservices via Eureka (`lb://`) et applique les préoccupations transverses de sécurité.

## Responsabilités clés
- **Authentification** (`JwtAuthFilter`) : valide la signature du JWT et **injecte l'identité** dans les en-têtes aval `X-User-Id` / `X-User-Role` / `X-User-Agence-Id` (extraits du token, jamais du corps).
- **Autorisation** (`RoleFilter`) : restreint certaines routes (ex. `/api/users/**`, `/api/analytics/**` → `SUPERVISOR, ADMIN`).
- **CORS** global, **rate-limiting** (Redis), `StripPrefix`.

## Points notables (soutenance)
- Routes publiques : `/api/auth/**`, `/api/channels/**` (webhooks), `GET /api/agences`, `/api/tickets/*/photos`.
- Route **self-service** `/api/users/me/**` : `JwtAuthFilter` seul (sans `RoleFilter`) → accessible à tout utilisateur authentifié, bornée à son propre compte.
- Le gateway centralise la sécurité : les services aval font confiance aux en-têtes `X-User-*` signés en amont.

## Dépendances
Eureka (découverte), Redis (rate-limiting), tous les microservices métier.
