# Notification Service

**Port :** `8086` · **Stack :** Spring Boot, Spring Mail, RabbitMQ · **Base de données :** `notif_db`

## Rôle
Envoi et journalisation des notifications (email). Découplé du chemin critique de traitement.

## Endpoints principaux
| Méthode | Chemin | Accès |
|---|---|---|
| GET | `/api/notifications` | Journal des envois (ADMIN) |

## Points notables (soutenance)
- **Asynchrone par événements** : consomme les événements RabbitMQ (ticket créé/résolu, décision finalisée, alerte SLA) et envoie les emails correspondants → le métier n'attend jamais l'email.
- Chaque envoi est tracé en base (`notif_db`) pour audit.

## Dépendances
Eureka, RabbitMQ, serveur SMTP, `notif_db`.
