# 📞 AutoAssist — Système de Gestion des Appels & Aide à la Décision

> Application web full-stack microservices pour la gestion des appels et l'aide à la décision dans le secteur de l'assurance automobile (pièces de rechange).

## 🏗️ Architecture

```
Frontend React :5173
    ↓ HTTP
API Gateway :8080  (Spring Cloud Gateway + JWT Filter)
    ↓ Eureka Discovery
┌────────────────────────────────────────────────────┐
│ auth-service :8081       │ organisation-service :8082│
│ ticket-service :8083     │ rules-service :8084       │
│ decision-service :8085   │ notification-svc :8086    │
│ analytics-service :8087                              │
└────────────────────────────────────────────────────┘
    ↓
ai-service :8000  (Python FastAPI + Whisper)
    ↓
PostgreSQL :5432  │  Redis :6379  │  RabbitMQ :5672
Eureka :8761      │  n8n :5678
```

## 🚀 Démarrage rapide

### Prérequis
- Docker & Docker Compose
- Node.js 18+ (pour le frontend en dev)
- Git

### Installation

```bash
# 1. Cloner le projet
git clone https://github.com/your-username/call-management-system.git
cd call-management-system

# 2. Configurer les variables d'environnement
cp .env.example .env
# Éditer .env avec vos secrets (JWT, OpenAI, SMTP, Twilio...)

# 3. Lancer tous les services backend
docker compose up -d

# 4. Lancer le frontend (en développement)
cd frontend && npm install && npm run dev

# 5. Vérifier que tout fonctionne
docker ps
```

### Accéder aux services

| Service | URL |
|---------|-----|
| **Application Web** | http://localhost:5173 |
| **API Gateway** | http://localhost:8080 |
| **Eureka Dashboard** | http://localhost:8761 |
| **RabbitMQ Dashboard** | http://localhost:15672 (guest/guest) |
| **n8n Automatisation** | http://localhost:5678 |
| **AI Service (Swagger)** | http://localhost:8000/docs |
| **Swagger (par service)** | http://localhost:{port}/swagger-ui.html |

## 🛠️ Stack technique

| Composant | Technologie |
|-----------|-------------|
| Backend | Spring Boot 3.3 · Java 17 · Maven |
| Frontend | React 18 · TypeScript · Vite |
| IA Audio | Python FastAPI · OpenAI Whisper |
| IA Décision | Moteur de règles JSONB + GPT-4o-mini (OpenAI) |
| Base de données | PostgreSQL 15 (1 DB par service) |
| Cache | Redis 7 |
| Messaging | RabbitMQ 3 (événements inter-services) |
| Automatisation | n8n (workflows email→ticket) |
| DevOps | Docker Compose |

## 📁 Structure du projet

```
call-management-system/
├── services/
│   ├── eureka-server/          Service Discovery
│   ├── api-gateway/            API Gateway + JWT + Rate Limiting
│   ├── auth-service/           Authentification JWT
│   ├── organisation-service/   Gestion Users & Agences
│   ├── ticket-service/         Tickets + Email IMAP + VoIP Twilio
│   ├── rules-service/          Moteur de règles d'assurance
│   ├── decision-service/       Scoring IA + courrier PDF
│   ├── notification-service/   Emails Thymeleaf + RabbitMQ
│   ├── analytics-service/      KPIs & Dashboard
│   └── ai-service/             Python FastAPI + Whisper STT + NLP
├── frontend/                   React 18 + Vite + TypeScript
├── infrastructure/
│   ├── postgres/               Scripts init multi-DB
│   └── nginx/                  Config reverse proxy (prod)
├── diagrammes_uml/             15 diagrammes PlantUML
├── docker-compose.yml          Orchestration complète
├── .env.example                Template variables d'environnement
└── seed-demo-data.ps1          Script de données de démonstration
```

## 🔐 Canaux d'acquisition

| Canal | Mécanisme |
|-------|-----------|
| 📞 **Appel (VoIP)** | Webhook Twilio → ticket automatique + enregistrement audio |
| 📧 **Email (IMAP)** | Listener IMAP Gmail → filtrage par mots-clés → ticket automatique |
| 🌐 **Web** | Formulaire public → API REST → ticket automatique |

## 👥 Rôles utilisateurs

| Rôle | Accès |
|------|-------|
| **OPERATOR** | Tickets de son agence, file d'attente, analyse IA |
| **SUPERVISOR** | + Règles d'assurance, KPIs, gestion agence |
| **ADMIN** | + Gestion tous utilisateurs, toutes agences, logs notifications |

## 📊 Modules fonctionnels

1. **Gestion Utilisateurs & Agences** — CRUD complet avec RBAC
2. **Acquisition Multicanale** — 3 canaux (CALL, EMAIL, WEB) + file d'attente temps réel
3. **Transcription Audio IA** — Whisper STT + NLP (sentiment, mots-clés, type sinistre)
4. **Moteur de Règles** — Éditeur visuel, versioning, évaluation JSONB
5. **Aide à la Décision** — Scoring IA (Rules → Cache Redis → GPT-4o-mini) + courrier PDF

## 📄 Licence

Projet de Fin d'Études (PFE) — Usage académique.
