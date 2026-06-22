# Organisation Service (Users & Agences)

**Port :** `8082` · **Stack :** Spring Boot, Spring Data JPA · **Base de données :** `users_db` (`users`, `agences`)

## Rôle
Référentiel des utilisateurs et des agences. **Source de vérité des identifiants de connexion** (`passwordHash` lu par l'auth-service).

## Endpoints principaux
| Méthode | Chemin | Accès |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/users` | SUPERVISOR/ADMIN (création & désactivation = **ADMIN**) |
| GET/PUT | `/api/users/me` · PATCH `/me/password` | **Tous les rôles** (self-service, borné à soi) |
| GET/POST/PUT/DELETE | `/api/agences` | Lecture publique ; CRUD = **ADMIN** |

## Points notables (soutenance)
- **RBAC fin** (`validateUpdatePermissions`) : OPERATOR → soi uniquement (rôle non modifiable), SUPERVISOR → son agence, ADMIN → tout le monde.
- Séparation **self-service `/me`** (tout rôle) vs **administration** (gestion d'autrui, gatée). L'identité vient de l'en-tête `X-User-Id` signé par la gateway.
- Endpoints internes (`/internal`, `/email/{email}`) appelés par l'auth-service via Feign.

## Dépendances
Eureka, RabbitMQ (événement `agence.created`), `users_db`.
