# Analytics Service

**Port :** `8087` · **Stack :** Spring Boot, Spring Data JPA · **Base de données :** `analytics_db` (`daily_stats`)

## Rôle
Calcul des KPIs et statistiques alimentant le dashboard superviseur. Réservé SUPERVISOR/ADMIN.

## Endpoints principaux
| Chemin | Description |
|---|---|
| `/api/analytics/kpis` (+ `/comparison`) | KPIs et comparaison de périodes |
| `/api/analytics/daily-stats`, `/operator-performance`, `/supervision-alerts` | Détails et alertes |

## Points notables (soutenance)
- **Cohérence des statuts** : les KPIs de statut sont recalculés à la demande auprès du **ticket-service** (source de vérité) plutôt que sommés depuis les instantanés `daily_stats` (qui double-comptaient), avec **repli résilient** si le service est indisponible.
- Job de **réconciliation** (détection de dérive) pour la fiabilité des chiffres.

## Dépendances
Eureka, ticket-service (Feign), `analytics_db`.
