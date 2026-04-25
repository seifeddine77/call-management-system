# 🔄 Guide n8n — Workflows AutoAssist

## Accès à n8n

- **URL** : http://localhost:5678
- **Email** : admin@autoassist.com
- **Password** : AutoAssist2026!

## Workflows disponibles

| # | Workflow | Fichier | Déclencheur |
|---|---------|---------|-------------|
| 1 | **Rapport Quotidien** | `workflow_rapport_quotidien.json` | Cron toutes les 24h |
| 2 | **Alerte SLA** | `workflow_alerte_sla.json` | Cron toutes les 30 min |
| 3 | **Webhook Web → Ticket** | `workflow_webhook_web.json` | HTTP POST `/webhook/autoassist-web-form` |

## Import des workflows

1. Ouvrir n8n → http://localhost:5678
2. Menu → **Workflows** → **Add Workflow** → **Import from File**
3. Sélectionner le fichier `.json` depuis `infrastructure/n8n/`
4. **Activer** le workflow (toggle en haut à droite)

## Configuration SMTP (pour les envois email)

Après import, configurer les credentials :
1. Aller dans **Settings → Credentials → Add Credential**
2. Type : **SMTP**
3. Paramètres :
   - Host : `smtp.gmail.com`
   - Port : `587`
   - User : `saifrezgui77@gmail.com`
   - Password : (app password Gmail)
   - SSL/TLS : `STARTTLS`

## Description des workflows

### 1. Rapport Quotidien
```
[Cron 24h] → [GET /api/tickets/stats] → [GET /api/analytics/summary] → [Formater HTML] → [Envoyer Email]
```
Envoie un résumé quotidien avec le nombre de tickets par statut au superviseur.

### 2. Alerte SLA
```
[Cron 30min] → [GET /api/tickets/queue] → [Filtrer > 2h] → [Envoyer Alerte]
```
Détecte les tickets NOUVEAU en attente depuis plus de 2 heures et envoie une alerte email.

### 3. Webhook Web → Ticket
```
[POST /webhook/autoassist-web-form] → [Valider] → [POST /api/channels/web] → [Réponse JSON]
```
Reçoit les soumissions de formulaires externes et crée un ticket WEB automatiquement.

**URL du webhook** : `http://localhost:5678/webhook/autoassist-web-form`

**Exemple d'appel** :
```bash
curl -X POST http://localhost:5678/webhook/autoassist-web-form \
  -H "Content-Type: application/json" \
  -d '{"clientNom":"Test n8n","clientTel":"+21650000000","clientEmail":"test@test.com","description":"Test via n8n webhook","priorite":2}'
```
