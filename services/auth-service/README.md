# Auth Service

**Port :** `8081` · **Stack :** Spring Boot, Spring Security, JJWT · **Base de données :** `auth_db` (`refresh_tokens`)

## Rôle
Authentification et gestion du cycle de vie des tokens JWT. Endpoint public (hors `RoleFilter`).

## Endpoints principaux
| Méthode | Chemin | Description |
|---|---|---|
| POST | `/api/auth/login` | Vérifie BCrypt, émet access (15 min) + refresh (7 j) |
| POST | `/api/auth/register` | Inscription publique — **rôle forcé OPERATOR** (anti-escalade) |
| POST | `/api/auth/refresh` | Rotation de session à partir d'un refresh token valide |

## Points notables (soutenance)
- **Ne stocke pas les utilisateurs** : il interroge l'organisation-service via **Feign** (`findByEmail` → récupère le `passwordHash`). L'organisation-service est la source de vérité des identifiants.
- Le `subject` du JWT = identifiant utilisateur ; le rôle est une *claim*. Le refresh **reprend le rôle des claims** (jamais d'une entrée client) → pas d'élévation de privilèges.
- Secrets JWT distincts access/refresh ; mots de passe hachés BCrypt.

## Dépendances
Eureka, organisation-service (Feign), `auth_db`.
