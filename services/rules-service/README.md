# Rules Service (Moteur de Règles)

**Port :** `8084` · **Stack :** Spring Boot, Spring Data JPA · **Base de données :** `rules_db`

## Rôle
Moteur de règles métier qui évalue une réclamation et produit une recommandation (FAVORABLE / DEFAVORABLE / ETUDE) avec un impact de score.

## Endpoints principaux
| Méthode | Chemin | Description |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/rules` | Administration des règles (SUPERVISOR/ADMIN) |
| POST | `/api/rules/evaluate` | Évalue un contexte ticket contre les règles actives |

## Points notables (soutenance)
- **Priorité DESC** : la règle de plus forte priorité gagne ; un verdict **ETUDE agit comme veto**.
- Score = base (selon décision) + Σ des `score_impact` (±50) + **analyse de mots-clés** du texte (transcription CALL ou description EMAIL/WEB), avec détection de négation.
- Règles 100 % configurables (sans redéploiement) → explicabilité : la liste des règles appliquées est retournée.

## Dépendances
Eureka, `rules_db`. Appelé par le decision-service (Feign).
