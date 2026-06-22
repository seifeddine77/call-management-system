# Ticket Service

**Port :** `8083` · **Stack :** Spring Boot, JPA, WebSocket (STOMP), RabbitMQ · **Base de données :** `tickets_db`

## Rôle
Cœur métier du cycle de vie des réclamations (création → résolution), acquisition **multicanale** et supervision.

## Endpoints principaux
| Domaine | Chemin |
|---|---|
| Gestion tickets | `/api/tickets/**` (liste, détail, statut, file d'attente, photos, audio) |
| Acquisition canaux | `/api/channels/{call,email,web}` (publics — webhooks externes) |
| Temps réel | WebSocket `/topic/tickets.update` |

## Points notables (soutenance)
- **Priorité unifiée** : détection automatique par mots-clés sur la description, **identique sur les 3 canaux** (CALL/EMAIL/WEB), surchargeable par une saisie explicite.
- **Cohérence sans FK inter-services** : publication d'événements RabbitMQ (`ticket.created` / `resolved` / `status-changed`) + *publisher confirms* + job de réconciliation.
- Preuves : photos sinistre + **audio chiffré AES-256-GCM**. Transitions de statut contrôlées (machine à états).
- RGPD : anonymisation événementielle (`ticket.anonymized`).

## Dépendances
Eureka, RabbitMQ, ai-service & organisation-service (Feign), `tickets_db`.
