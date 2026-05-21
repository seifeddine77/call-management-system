# Spécification du Diagramme d'Architecture Physique AutoAssist

## Objectif
Créer un **diagramme de déploiement** professionnel montrant les **17 conteneurs Docker** du système AutoAssist, organisés en 5 couches. Le diagramme doit être en format **paysage** (landscape), clair, lisible, et occuper toute la page A4 en mode paysage.

## Dimensions cibles
- Format : **paysage A4** (297mm × 210mm)
- Résolution : minimum **3000×2000 pixels** à 300 DPI
- Fond blanc

---

## Architecture : 5 couches (de gauche à droite ou de haut en bas)

### 1. 🖥️ Couche Présentation (2 conteneurs)
| Conteneur | Technologie | Port |
|-----------|------------|------|
| **Frontend React** | React 18 + Vite | 3000 |
| **Mobile Flutter** | Flutter Web | 5555 |

### 2. 🔒 Couche Gateway (1 conteneur)
| Conteneur | Technologie | Port |
|-----------|------------|------|
| **API Gateway** | Spring Cloud Gateway | 8080 |
> Fonctions : JWT validation, RBAC filter, Rate Limiter (Redis)

### 3. 📡 Couche Discovery (1 conteneur)
| Conteneur | Technologie | Port |
|-----------|------------|------|
| **Eureka Server** | Netflix Eureka | 8761 |
> Tous les microservices + gateway s'enregistrent auprès d'Eureka

### 4. ⚙️ Couche Métier — 9 Microservices
| Conteneur | Technologie | Port | Base de données |
|-----------|------------|------|-----------------|
| **auth-service** | Java/Spring Boot | 8081 | auth_db |
| **organisation-service** | Java/Spring Boot | 8082 | users_db |
| **ticket-service** | Java/Spring Boot | 8083 | tickets_db |
| **rules-service** | Java/Spring Boot | 8084 | rules_db |
| **decision-service** | Java/Spring Boot | 8085 | decisions_db |
| **notification-service** | Java/Spring Boot | 8086 | notif_db |
| **analytics-service** | Java/Spring Boot | 8087 | analytics_db |
| **ai-service** | Python/FastAPI | 8000 | ai_db |
| **whatsapp-service** | Node.js | — | — |

### 5. 🗄️ Couche Infrastructure (4 conteneurs)
| Conteneur | Technologie | Port | Rôle |
|-----------|------------|------|------|
| **PostgreSQL 15** | PostgreSQL Alpine | 5432 | 9 bases de données |
| **Redis 7** | Redis Alpine | 6379 | Cache décisions (SHA-256, TTL 1h) |
| **RabbitMQ 3** | RabbitMQ Management | 5672/15672 | Files de messages asynchrones |
| **n8n** | n8n.io | 5678 | Automatisation — 8 workflows |

### ☁️ Services Externes (hors Docker)
| Service | Rôle |
|---------|------|
| **Docker Model Runner** (Gemma 4 via llama.cpp) | LLM local, port 12434 |
| **ChromaDB** | Base vectorielle pour RAG |
| **Twilio** | VoIP + SMS + WhatsApp |
| **SMTP** | Email sortant (notification) |
| **IMAP** | Email entrant (ticket) |

---

## Connexions principales

### Client → Gateway
- Frontend React → API Gateway (HTTP/WebSocket)
- Mobile Flutter → API Gateway (HTTP)

### Gateway → Microservices (REST via Eureka)
- API Gateway → auth, organisation, ticket, rules, decision, notification, analytics

### Inter-services (REST/Feign)
- ticket-service → ai-service (HTTP : transcription + NLP)
- decision-service → rules-service (Feign : évaluation règles)
- decision-service → Docker Model Runner (LLM Gemma 4)
- ai-service → Docker Model Runner (LLM RAG)
- ai-service → ChromaDB (recherche vectorielle)
- whatsapp-service → ai-service (HTTP : RAG chatbot)

### Services → Infrastructure
- 8 services Java + ai-service + n8n → PostgreSQL (JDBC)
- decision-service + gateway → Redis (cache + rate limit)
- organisation, ticket, decision, rules → RabbitMQ (publish)
- notification, analytics, whatsapp → RabbitMQ (subscribe)
- n8n → RabbitMQ (webhook)

### Services → Externes
- ticket-service → Twilio (VoIP SDK)
- ticket-service → IMAP (email entrant)
- notification-service → SMTP (email sortant)

---

## Code Mermaid (référence)

```mermaid
graph LR
    subgraph CLIENTS["🖥️ Présentation"]
        FE["Frontend React :3000"]
        MOB["Mobile Flutter :5555"]
    end

    subgraph GATEWAY["🔒 Gateway"]
        GW["API Gateway :8080<br/>JWT + RBAC"]
    end

    subgraph DISCOVERY["📡 Discovery"]
        EUR["Eureka Server :8761"]
    end

    subgraph SERVICES["⚙️ Microservices"]
        AUTH["auth :8081"]
        ORG["organisation :8082"]
        TKT["ticket :8083"]
        RULES["rules :8084"]
        DEC["decision :8085"]
        NOTIF["notification :8086"]
        ANLYT["analytics :8087"]
        AI["ai-service :8000"]
        WA["whatsapp"]
    end

    subgraph INFRA["🗄️ Infrastructure"]
        PG["PostgreSQL :5432"]
        RD["Redis :6379"]
        RMQ["RabbitMQ :5672"]
        N8N["n8n :5678"]
    end

    subgraph EXTERNAL["☁️ Externes"]
        LLM["Gemma 4 :12434"]
        CHROMA["ChromaDB"]
        TWILIO["Twilio"]
        SMTP["SMTP"]
        IMAP_SRV["IMAP"]
    end

    FE --> GW
    MOB --> GW
    GW --> AUTH & ORG & TKT & RULES & DEC & NOTIF & ANLYT

    AUTH & ORG & TKT & RULES & DEC & NOTIF & ANLYT & AI & GW -.-> EUR

    TKT --> AI
    DEC --> RULES
    DEC & AI --> LLM
    AI --> CHROMA
    WA --> AI
    TKT --> TWILIO & IMAP_SRV
    NOTIF --> SMTP

    AUTH & ORG & TKT & RULES & DEC & NOTIF & ANLYT & AI & N8N --> PG
    DEC & GW --> RD
    ORG & TKT & DEC & RULES --> RMQ
    NOTIF & ANLYT & WA & N8N --> RMQ
```

---

## Style recommandé
- Couleurs par couche :
  - Présentation : **bleu clair** (#4FC3F7)
  - Gateway : **orange** (#FFB74D)
  - Discovery : **violet** (#CE93D8)
  - Microservices : **vert** (#81C784)
  - Infrastructure : **rouge clair** (#EF9A9A)
  - Externes : **gris** (#B0BEC5)
- Texte : Arial ou sans-serif, taille 12-14px
- Bordures : 2px, couleur foncée assortie
- Flèches : lignes fines pour les connexions directes, pointillés pour Eureka
