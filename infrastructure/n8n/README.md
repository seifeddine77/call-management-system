# 🔄 Guide n8n — Workflows AutoAssist

## Accès à n8n

- **URL** : http://localhost:5678
- **Email** : admin@autoassist.com
- **Password** : AutoAssist2026!

## Workflows disponibles

| # | Workflow | Fichier | Déclencheur | Services utilisés |
|---|---------|---------|-------------|-------------------|
| 1 | **📊 Rapport Quotidien** | `workflow_rapport_quotidien.json` | Cron 24h | ticket, analytics |
| 2 | **⚠️ Alerte SLA (Email)** | `workflow_alerte_sla.json` | Cron 30min | ticket |
| 3 | **🌐 Webhook Web → Ticket** | `workflow_webhook_web.json` | HTTP POST | ticket |
| 4 | **📱 Alerte SLA (SMS Twilio)** | `workflow_whatsapp_alerte.json` | Cron 30min | ticket, Twilio |
| 5 | **🏥 Health Check** | `workflow_health_check.json` | Cron 5min | tous (9 services) |
| 6 | **🚀 DevOps Pipeline** | `workflows/devops-pipeline-notifications.json` | Webhook GitHub/Prometheus | ticket |
| 7 | **🤖 Auto-Analyse IA** | `workflow_auto_analyse_ia.json` | Cron 10min | ticket, decision, ai (Gemma 4) |
| 8 | **🧠 Résumé IA Quotidien** | `workflow_resume_ia_quotidien.json` | Cron 18h | ticket, analytics, ai (Gemma 4 RAG) |
| 9 | **⚙️ Traitement IA bout-en-bout** | `workflow_traitement_ia_bout_en_bout.json` | Manuel + Cron 15min | ticket, decision (Rules+Gemma), lettre PDF, email |
| 10 | **🚨 Détection de pic d'activité** | `workflow_detection_pic.json` | Cron 1h | analytics (comparison) |
| 11 | **📲 Alerte SLA → Groupe WhatsApp** | `workflow_sla_whatsapp_groupe.json` | Cron 30min | organisation, ticket, whatsapp (notify-group) |

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

### 1. 📊 Rapport Quotidien
```
[Cron 24h] → [GET /api/tickets/stats] → [GET /api/analytics/kpis] → [Formater HTML] → [Email]
```

### 2. ⚠️ Alerte SLA (Email)
```
[Cron 30min] → [GET /api/tickets/queue] → [Filtrer > 2h] → [Email Alerte]
```

### 3. 🌐 Webhook Web → Ticket
```
[POST /webhook/autoassist-web-form] → [Valider] → [POST /api/channels/web] → [Réponse JSON]
```

### 4. 📱 Alerte SLA (SMS Twilio)
```
[Cron 30min] → [GET /api/tickets/queue] → [Filtrer > 2h] → [SMS via Twilio]
```

### 5. 🏥 Health Check Microservices
```
[Cron 5min] → [GET /health × 9 services] → [Analyser] → [Email si DOWN]
```

### 6. 🚀 DevOps Pipeline Notifications
```
[Webhook GitHub] → [Success?] → [Email succès/échec]
[Webhook Prometheus] → [Critical?] → [Email + Auto-Create Ticket]
```

### 7. 🤖 Auto-Analyse IA des Tickets (NOUVEAU)
```
[Cron 10min] → [GET Tickets NOUVEAU] → [Vérifier si décision existe]
    → [Extraire type pièce/sinistre] → [POST /api/decisions/analyze]
    → [Formater résultats] → [Email rapport IA au superviseur]
```
**Chaîne 4 microservices** : ticket-service → decision-service (Rules + Gemma 4) → notification par email.
Le workflow détecte automatiquement le type de pièce et sinistre depuis la description du ticket.

### 8. 🧠 Résumé IA Quotidien via Gemma 4 (NOUVEAU)
```
[Cron 18h] → [GET Stats + KPIs + Comparaison] (en parallèle)
    → [Construire question enrichie] → [POST /chat/ (RAG + Gemma 4)]
    → [Formater email HTML] → [Email résumé IA au superviseur]
```
**Utilise le chatbot RAG** pour générer un résumé en langage naturel avec recommandations.
Au lieu de chiffres bruts, le superviseur reçoit une analyse intelligente rédigée par Gemma 4.

### 9. ⚙️ Traitement IA bout-en-bout (décision + lettre)
```
[Manuel / Cron 15min] → [GET tickets NOUVEAU] → [Filtrer + extraire type pièce/sinistre]
   → [POST /api/decisions/analyze (Rules + Gemma 4)] → [Combiner & router]
   → IF décision tranchée ?
        ├─ OUI → [GET lettre PDF /decisions/{id}/letter] → [Email client + PDF joint]
        └─ NON (ETUDE) → [Email escalade au superviseur]
```
**Automatisation métier complète** : de l'arrivée d'une réclamation jusqu'à l'envoi de la
lettre de décision officielle (PDF) au client — ou l'escalade humaine si l'IA ne tranche pas.
Chaîne **ticket → decision (Rules + Gemma 4) → lettre PDF → email**.

- Déclencheur **manuel** inclus pour la démo live ; **Cron 15 min** pour la production.
- Limité à 3 tickets par exécution (constante `MAX` dans le nœud « Filtrer ») — augmenter en prod.
- Destinataire configurable via la variable d'environnement `SUPERVISOR_EMAIL`
  (par sécurité, la lettre client est envoyée au superviseur ; en production, router vers `clientEmail`).
- Variable `MONITORING_AGENCE_ID` requise pour le workflow DevOps (auto-création de ticket sur alerte critique).

### 10. 🚨 Détection de pic d'activité (supervision proactive)
```
[Cron 1h] → [GET /api/analytics/kpis/comparison?period=7d]
   → [Évaluer le pic : deltaTickets > +50% et volume >= 10] → [Alerte surcharge superviseur]
```
**Supervision proactive** : prévient une montée en charge / un incident *avant* dégradation,
en comparant le volume courant à la période précédente. Seuils ajustables dans le nœud
« Évaluer le pic » (`SEUIL_PCT`, `MIN_TICKETS`). N'envoie un email que si le seuil est franchi.

### 11. 📲 Alerte SLA → Groupe WhatsApp de l'agence
```
[Cron 30min] → [GET agences] → (par agence) [GET /api/tickets/queue?agenceId=X]
   → [Détecter dépassement > 120 min] → [POST callmgmt-whatsapp:3000/notify-group]
                                          → message dans le groupe "AutoAssist — <agence>"
```
**Alerte ciblée** : chaque agence reçoit, dans **son propre groupe WhatsApp**, la liste de ses
tickets en dépassement SLA — bien plus actionnable qu'un email générique. Repose sur le nouvel
endpoint `POST /notify-group` du whatsapp-service (envoi via whatsapp-web.js). Seuil ajustable
(`SLA_MIN`) dans le nœud « Détecter dépassement ».
