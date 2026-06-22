# Decision Service (Aide à la Décision)

**Port :** `8085` · **Stack :** Spring Boot, Resilience4j, Redis · **Base de données :** `decisions_db`

## Rôle
Orchestrateur de la décision d'acceptation : combine le **moteur de règles** et le **scoring IA**, persiste la décision et la publie.

## Endpoints principaux
| Méthode | Chemin | Description |
|---|---|---|
| POST | `/api/decisions/analyze` | Calcule score + décision (RULES / AI / HYBRID) |
| POST | `/api/decisions/{id}/override` | Finalisation/override par un superviseur |
| POST | `/api/decisions/{id}/letter` | Génération du courrier de réponse |

## Points notables (soutenance)
- **Circuit-breaker (Resilience4j)** : dégrade proprement si le rules-service ou l'IA est indisponible (source = `CIRCUIT_BREAKER`).
- **Cache Redis** (TTL) : évite de recalculer une décision identique.
- **Explicabilité** : règles appliquées + source de décision tracées ; publication `decision.finalized` (RabbitMQ).

## Dépendances
Eureka, rules-service (Feign), ai-service, Redis, RabbitMQ, `decisions_db`.
