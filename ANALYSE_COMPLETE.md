# 📊 ANALYSE COMPLÈTE DU PROJET AUTOASSIST

> **Projet :** Plateforme Microservices Intelligente pour la Gestion des Sinistres Automobiles  
> **Date d'analyse :** 11 Mai 2026  
> **Architecture :** 10 Microservices (Java Spring Boot + Python FastAPI + Node.js)

---

## 📑 TABLE DES MATIÈRES

1. [Vue d'Ensemble](#1-vue-densemble)
2. [Architecture Globale](#2-architecture-globale)
3. [API Gateway (Port 8080)](#3-api-gateway-port-8080)
4. [Auth Service (Port 8081)](#4-auth-service-port-8081)
5. [Organisation Service (Port 8082)](#5-organisation-service-port-8082)
6. [Ticket Service (Port 8083)](#6-ticket-service-port-8083)
7. [Rules Service (Port 8084)](#7-rules-service-port-8084)
8. [Decision Service (Port 8085)](#8-decision-service-port-8085)
9. [Notification Service (Port 8086)](#9-notification-service-port-8086)
10. [Analytics Service (Port 8087)](#10-analytics-service-port-8087)
11. [AI Service (Port 8000)](#11-ai-service-port-8000)
12. [WhatsApp Service](#12-whatsapp-service)
13. [Infrastructure & Communication](#13-infrastructure--communication)

---

## 1. VUE D'ENSEMBLE

### 1.1 Stack Technologique

| Couche | Technologies |
|--------|-------------|
| **Backend** | Java 17, Spring Boot 3.3, Spring Cloud |
| **IA/ML** | Python 3.11, FastAPI, Whisper, spaCy, ChromaDB |
| **Frontend** | React 18, Axios, WebSocket |
| **Base de données** | PostgreSQL 15, Redis 7, ChromaDB |
| **Message Queue** | RabbitMQ 3.12 |
| **DevOps** | Docker, Docker Compose, Kubernetes |
| **Sécurité** | JWT (jjwt 0.12.3), AES-256-GCM, BCrypt |

### 1.2 Liste des Microservices

| Service | Port | Langage | Responsabilité |
|---------|------|---------|----------------|
| `eureka-server` | 8761 | Java | Service Discovery |
| `api-gateway` | 8080 | Java | Routage, JWT, Rate Limiting |
| `auth-service` | 8081 | Java | Authentification, Refresh Tokens |
| `organisation-service` | 8082 | Java | Users, Agences, RBAC |
| `ticket-service` | 8083 | Java | Tickets multicanaux, File d'attente |
| `rules-service` | 8084 | Java | Moteur de règles JSONB |
| `decision-service` | 8085 | Java | Scoring IA, PDF, Circuit Breaker |
| `notification-service` | 8086 | Java | Emails Thymeleaf, RabbitMQ |
| `analytics-service` | 8087 | Java | KPIs, Dashboard, Aggregation |
| `ai-service` | 8000 | Python | Whisper STT, NLP, RAG Chatbot |
| `whatsapp-service` | - | Node.js | Chatbot WhatsApp |

---

## 2. ARCHITECTURE GLOBALE

### 2.1 Flux de Requêtes

```
┌─────────────┐
│   Frontend  │ (React - localhost:3000)
│  (React)    │
└──────┬──────┘
       │ HTTP/JWT
       ▼
┌─────────────┐
│ API Gateway │ (Port 8080)
│  - JWT Auth │
│  - Rate Limit
└──────┬──────┘
       │
       ├─────────────────────────────────────────────────────┐
       │                                                     │
       ▼                                                     ▼
┌──────────────┐  Feign  ┌──────────────┐           ┌──────────────┐
│auth-service  │◄───────►│organisation  │           │ticket-service│
│:8081         │         │-service:8082 │           │:8083         │
└──────────────┘         └──────────────┘           └──────┬───────┘
                                                          │
       ┌──────────────────────────────────────────────────┼──────┐
       │                                                  │      │
       ▼                                                  ▼      ▼
┌──────────────┐  Feign  ┌──────────────┐         ┌──────────────┐
│rules-service │◄───────►│decision      │         │ai-service    │
│:8084         │         │-service:8085  │         │:8000 (Python)│
└──────────────┘         └──────┬───────┘         └──────────────┘
                                │
                                ▼
                       ┌──────────────┐
                       │notification  │
                       │-service:8086 │
                       └──────────────┘
```

### 2.2 Patterns Utilisés

- **API Gateway Pattern** : Point d'entrée unique
- **Service Discovery** : Netflix Eureka
- **Circuit Breaker** : Resilience4j
- **Event-Driven** : RabbitMQ pour async
- **CQRS** : Séparation commandes/requêtes
- **Saga Pattern** : Transactions distribuées

---

## 3. API GATEWAY (Port 8080)

### 3.1 Fichiers Clés

| Fichier | Lignes | Rôle |
|---------|--------|------|
| `JwtAuthFilter.java` | 107 | Validation JWT + Headers |
| `RoleFilter.java` | 71 | Vérification RBAC |
| `RateLimiterConfig.java` | 39 | Limitation par IP |
| `application.yml` | 184 | Routes + Config |

### 3.2 Configuration des Routes (application.yml)

```yaml
# Routes publiques (sans JWT)
- /api/auth/**              → auth-service
- /api/agences (GET)        → organisation-service
- /api/channels/**          → ticket-service (webhooks)

# Routes protégées (JWT requis)
- /api/users/**             → organisation-service + RoleFilter(SUPERVISOR,ADMIN)
- /api/tickets/**           → ticket-service + JwtAuthFilter
- /api/rules/**             → rules-service + RoleFilter(SUPERVISOR,ADMIN)
- /api/decisions/**         → decision-service + JwtAuthFilter
- /api/notifications/**     → notification-service + RoleFilter(ADMIN)
- /api/analytics/**         → analytics-service + RoleFilter(SUPERVISOR,ADMIN)
```

### 3.3 JwtAuthFilter - Fonctionnement

```java
// Extraction du token Bearer
String token = authHeader.substring(7);

// Validation avec secret HMAC
SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
Jws<Claims> claims = Jwts.parser()
    .verifyWith(key)
    .build()
    .parseSignedClaims(token);

// Ajout headers pour downstream
X-User-Id: {userId}
X-User-Email: {email}
X-User-Role: {role}
X-User-Agence-Id: {agenceId}
```

### 3.4 Rate Limiting

- **Replenish Rate** : 100 tokens/sec
- **Burst Capacity** : 120 requêtes
- **Clé** : IP client (X-Forwarded-For supporté)

---

## 4. AUTH SERVICE (Port 8081)

### 4.1 Architecture JWT

**Access Token** (15 min / 24h en dev) :
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "OPERATOR",
  "agenceId": "agence-uuid",
  "type": "ACCESS",
  "iat": 1234567890,
  "exp": 1234567905
}
```

**Refresh Token** (7 jours) :
```json
{
  "sub": "user-uuid",
  "type": "REFRESH",
  "exp": 1235172690
}
```

### 4.2 Fichiers Clés

| Fichier | Rôle |
|---------|------|
| `AuthController.java` | Endpoints /login, /register, /refresh, /logout |
| `AuthServiceImpl.java` | Logique métier, Feign vers organisation-service |
| `JwtUtils.java` | Génération/validation JWT (2 secrets différents) |
| `RefreshToken.java` | Entity pour révocation |

### 4.3 Flow Authentification

```
1. POST /api/auth/login
   → Appel Feign GET /api/users/email/{email}
   → Vérification BCrypt du password
   → Génération access + refresh tokens
   → Stockage refresh token en DB
   
2. POST /api/auth/refresh
   → Validation JWT refresh token
   → Vérification en DB (non révoqué, non expiré)
   → Révocation ancien token
   → Génération nouvelle paire
   
3. POST /api/auth/logout
   → Révocation refresh token en DB
```

---

## 5. ORGANISATION SERVICE (Port 8082)

### 5.1 Modèle de Données

**Entity User :**
```java
@Entity
public class User {
    @Id @GeneratedValue UUID id;
    String email;              // Unique
    String passwordHash;       // BCrypt
    String nom, prenom;
    String telephone;
    String role;               // OPERATOR, SUPERVISOR, ADMIN
    
    @ManyToOne 
    Agence agence;             // CDC 1.A: OP/SUP doit avoir agence
    
    String planning;           // Horaires JSON
    boolean actif;
    LocalDateTime lastLogin;
}
```

**Entity Agence :**
```java
@Entity
public class Agence {
    UUID id;
    String nom;
    String adresse;
    String telephone, email;
    UUID responsableId;        // CDC 1.B: doit être SUPERVISOR
    String stockDescription;
    String whatsappGroupId;      // Pour chatbot
    boolean actif;
}
```

### 5.2 RBAC (Role-Based Access Control)

| Rôle | Permissions |
|------|-------------|
| **OPERATOR** | Voir/modifier son profil uniquement, prendre tickets de son agence |
| **SUPERVISOR** | CRUD users de son agence, voir analytics agence |
| **ADMIN** | Tout, suppression, gestion agences |

### 5.3 Événements RabbitMQ

```java
// UserCreatedEvent → whatsapp-service écoute
user.exchange / user.created

// AgenceCreatedEvent → création groupe WhatsApp auto
user.exchange / agence.created
{
  "agenceId": "...",
  "nom": "Agence Casablanca",
  "telephone": "+212...",
  "email": "...",
  "adresse": "..."
}
```

---

## 6. TICKET SERVICE (Port 8083)

### 6.1 Acquisition Multicanale

**Canaux supportés :**
1. **CALL** - Appels VoIP (Twilio/Asterisk)
2. **EMAIL** - Parsing IMAP
3. **WEB** - Formulaire public

**Comportement par canal :**
| Canal | Auto-assign | Consentement | Priorité |
|-------|-------------|--------------|----------|
| CALL | Oui (opérateur en ligne) | Implicite (IVR) | 2 (Haute) |
| EMAIL | Non (file d'attente) | Implicite | 3 (Normale) |
| WEB | Non (file d'attente) | Explicite (checkbox) | 3 (Normale) |

### 6.2 File d'Attente (Queue)

**Algorithmes :**
```java
// Tri: Priorité ASC puis Date création ASC
List<Ticket> queue = ticketRepository
    .findByStatutAndOperateurIdIsNullOrderByPrioriteAscCreatedAtAsc(NOUVEAU);

// RBAC: OPERATOR ne voit que sa file agence
if (OPERATOR.equals(role)) {
    return queue.stream()
        .filter(t -> t.getAgenceId().equals(userAgenceId))
        .toList();
}
```

### 6.3 RGPD & Sécurité

**Chiffrement Audio (AES-256-GCM) :**
```java
@Service
public class AudioEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    
    public byte[] encrypt(byte[] plainData) {
        // Génération IV aléatoire 12 bytes
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        
        // Format: [IV 12 bytes][ciphertext + auth tag]
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        
        return ByteBuffer.allocate(iv.length + cipherText.length)
            .put(iv)
            .put(cipherText)
            .array();
    }
}
```

### 6.4 WebSocket Notifications

```java
// Création ticket
messagingTemplate.convertAndSend("/topic/tickets.new", ticket);

// Mise à jour statut
messagingTemplate.convertAndSend("/topic/tickets.update", ticket);
```

### 6.5 Twilio Integration

**Endpoints Webhook :**
```
POST /api/channels/twilio/voice      → Appel entrant
POST /api/channels/twilio/recording → Enregistrement terminé
POST /api/channels/twilio/status     → Statut appel
```

**TwiML Response :**
```xml
<Response>
    <Say language="fr-FR">Bienvenue... Votre demande #{numero}</Say>
    <Record maxLength="300" 
            recordingStatusCallback="/api/channels/twilio/recording"
            transcribe="false" 
            playBeep="true"/>
    <Say>Merci pour votre appel...</Say>
</Response>
```

---

## 7. RULES SERVICE (Port 8084)

### 7.1 Moteur de Règles JSONB

**Format Condition :**
```json
{
  "operator": "AND",
  "conditions": [
    {"field": "typePiece", "op": "EQ", "value": "MECANIQUE"},
    {"field": "ageVehiculeAns", "op": "LTE", "value": 5},
    {"field": "kilometrage", "op": "LTE", "value": 50000}
  ]
}
```

**Opérateurs Supportés :**
- Logiques : `AND`, `OR`
- Comparaison : `EQ`, `NEQ`, `LT`, `GT`, `LTE`, `GTE`, `CONTAINS`, `IN`

### 7.2 Évaluation des Règles

**Algorithme (RuleService.evaluate) :**
```java
1. Récupérer toutes les règles actives (ORDER BY priorite DESC)
2. Évaluer chaque règle contre les données
3. Collecter les règles matchées
4. Analyse des résultats :
   
   Si (FAVORABLE && DEFAVORABLE) → Conflit, Ambiguous=true
   Si (FAVORABLE seul) → Score 40-98, Ambiguous=false
   Si (DEFAVORABLE seul) → Score 40-98, Ambiguous=false
   Si (aucune) → ETUDE, Score 30, Ambiguous=true
```

### 7.3 Analyse Mots-Clés Transcription

```java
// Bonus/Malus selon keywords
String[] garantieKeywords = {
    "sous garantie", "défaut de fabrication", "vice caché"
}; // +4 par mot

String[] fraudeKeywords = {
    "fausse déclaration", "compteur trafiqué", "frauduleux"
}; // -7 par mot

// Limité à [-20, +20]
```

### 7.4 Versioning (CDC 4.D)

```java
@Transactional
public RuleResponse updateRule(UUID ruleId, UpdateRuleRequest request) {
    // 1. Sauvegarder ancienne version
    RuleVersion version = RuleVersion.builder()
        .ruleId(rule.getId())
        .conditions(rule.getConditions())
        .version(rule.getVersion())
        .build();
    ruleVersionRepository.save(version);
    
    // 2. Incrémenter version
    rule.setVersion((short) (rule.getVersion() + 1));
    
    // 3. Appliquer modifications
    ...
}
```

---

## 8. DECISION SERVICE (Port 8085)

### 8.1 Flux de Décision 3 Étapes

```
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 1: Cache Redis Lookup                                  │
│ → Même ticket déjà analysé ? → Retourner décision enregistrée│
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 2: Rules Engine                                       │
│ → Appel Feign à rules-service/evaluate                      │
│ → Score ≥ 60 et non ambiguous ? → Décision RULES           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 3: IA (Fallback)                                      │
│ → Cas ambigus ou score < 60                                 │
│ → Appel LLM (Gemma 4 via Ollama)                            │
│ → Circuit Breaker: fallback ETUDE si IA indisponible        │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Circuit Breaker Pattern

```java
@CircuitBreaker(name = "llm", fallbackMethod = "fallbackDecision")
public DecisionResult analyzeWithAI(DecisionRequest request) {
    // Appel Ollama API
    String prompt = buildPrompt(request, rulesResult);
    String aiResponse = callOllama(prompt);
    return parseAIResponse(aiResponse);
}

// Fallback exécuté si LLM down
public DecisionResult fallbackDecision(DecisionRequest request, Exception ex) {
    return DecisionResult.builder()
        .decision("ETUDE")
        .source("CIRCUIT_BREAKER")
        .motif("IA temporairement indisponible - décision manuelle requise")
        .build();
}
```

### 8.3 Sources de Décision

| Source | Description |
|--------|-------------|
| `RULES` | Décision claire par règles métier |
| `AI` | Décision pure IA (cas ambigus) |
| `HYBRID` | Règles + IA (conflit résolu) |
| `CIRCUIT_BREAKER` | Fallback quand IA indisponible |

### 8.4 Génération PDF

```java
@PostMapping("/{id}/letter")
public ResponseEntity<byte[]> generateLetter(@PathVariable UUID id) {
    Decision decision = decisionRepository.findById(id);
    
    // Génération PDF avec iText/OpenPDF
    byte[] pdf = letterService.generatePdf(decision);
    
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=decision.pdf")
        .body(pdf);
}
```

---

## 9. NOTIFICATION SERVICE (Port 8086)

### 9.1 Événements Consommés

```java
@RabbitListener(queues = "notification.queue")
public void handleTicketCreated(TicketCreatedEvent event) {
    // Envoi email confirmation client
    sendEmail(event.getClientEmail(), "TICKET_CREATED", context);
}

@RabbitListener(queues = "notification.queue")
public void handleTicketResolved(TicketResolvedEvent event) {
    // Email résolution avec feedback
    sendEmail(event.getClientEmail(), "TICKET_RESOLVED", context);
}

@RabbitListener(queues = "notification.queue")
public void handleDecisionFinalized(DecisionFinalizedEvent event) {
    // Email décision avec PDF attaché
    sendEmailWithAttachment(event.getClientEmail(), "DECISION", pdf);
}
```

### 9.2 Templates Thymeleaf

```html
<!-- ticket-created.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <h2>Bonjour <span th:text="${clientNom}">Client</span>,</h2>
    <p>Votre demande <strong th:text="${ticketNumero}">T-2024-00001</strong> 
       a été enregistrée.</p>
    <p>Canal: <span th:text="${canal}">EMAIL</span></p>
    <p>Nous vous recontacterons sous peu.</p>
</body>
</html>
```

### 9.3 Retry Mechanism

```java
@Entity
public class NotificationLog {
    String status; // PENDING, SENT, FAILED
    Short attempts; // Max 3 tentatives
    String errorMessage;
    
    @Scheduled(fixedDelay = 300000) // Toutes les 5 min
    public void retryFailedNotifications() {
        List<NotificationLog> failed = repository
            .findByStatusAndAttemptsLessThan("FAILED", (short) 3);
        
        failed.forEach(this::sendEmail);
    }
}
```

---

## 10. ANALYTICS SERVICE (Port 8087)

### 10.1 KPIs Calculés

| KPI | Formule |
|-----|---------|
| Taux Résolution | `ticketsResolus / totalTickets * 100` |
| Temps Moyen Traitement | Moyenne `resolvedAt - createdAt` |
| SLA Respect | `% tickets résolus sous 24h` |
| Répartition Canal | `COUNT GROUP BY canal` |
| Répartition Décisions | `COUNT GROUP BY finalDecision` |

### 10.2 Agrégation Quotidienne

```java
@Scheduled(cron = "0 0 2 * * *") // 2h du matin
public void aggregateDailyStats() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    
    DailyStat stat = DailyStat.builder()
        .date(yesterday)
        .totalTickets(countTickets(yesterday))
        .ticketsCall(countByCanal(yesterday, "CALL"))
        .ticketsEmail(countByCanal(yesterday, "EMAIL"))
        .decisionsFavorable(countDecisions(yesterday, "FAVORABLE"))
        .tempsMoyenMinutes(calculateAvgTime(yesterday))
        .build();
    
    dailyStatRepository.save(stat);
}
```

### 10.3 Export CSV

```java
@GetMapping("/export/csv")
public ResponseEntity<byte[]> exportCsv(@RequestParam String period) {
    List<DailyStat> stats = getStatsForPeriod(period);
    
    StringBuilder csv = new StringBuilder();
    csv.append("Date,Total,Nouveau,En Cours,Résolu,Fermé,Appel,Email,Web\n");
    
    stats.forEach(s -> csv.append(String.format(
        "%s,%d,%d,%d,%d,%d,%d,%d,%d\n",
        s.getDate(), s.getTotalTickets(), ...
    )));
    
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv")
        .header("Content-Disposition", "attachment; filename=analytics.csv")
        .body(csv.toString().getBytes());
}
```

---

## 11. AI SERVICE (Port 8000)

### 11.1 Architecture Python

```
app/
├── main.py              # Entry point FastAPI + Eureka
├── core/
│   ├── config.py        # Settings (env vars)
│   └── database.py      # SQLAlchemy + PostgreSQL
├── routers/
│   ├── transcription.py  # Whisper endpoints
│   ├── analysis.py       # NLP endpoints
│   ├── streaming.py      # Streaming audio
│   └── chat.py           # RAG chatbot
├── rag/
│   ├── rag_service.py    # Retrieval + Generation
│   └── knowledge_base.py # Documents vectorisés
└── models.py            # SQLAlchemy entities
```

### 11.2 Transcription Whisper

```python
@app.post("/transcribe/quick")
async def transcribe_audio(file: UploadFile):
    # Chargement modèle (lazy loading)
    model = request.app.state.whisper_model
    
    # Transcription
    result = model.transcribe(audio_path, language="fr")
    
    return {
        "text": result["text"],
        "language": result["language"],
        "duration_seconds": result["duration"]
    }
```

### 11.3 NLP Analysis (spaCy)

```python
@app.post("/analyze/text")
async def analyze_text(request: AnalysisRequest):
    nlp = request.app.state.nlp  # fr_core_news_sm
    doc = nlp(request.text)
    
    # Extraction entités
    entities = [(ent.text, ent.label_) for ent in doc.ents]
    
    # Détection type sinistre
    sinistre_type = detect_sinistre_type(doc)
    
    # Sentiment analysis
    sentiment = analyze_sentiment(doc)
    
    # Extraction mots-clés
    keywords = [token.lemma_ for token in doc if token.pos_ in ["NOUN", "ADJ"]]
    
    return {
        "type_sinistre": sinistre_type,
        "sentiment": sentiment,  # positif, négatif, neutre
        "keywords": keywords,
        "entities": entities
    }
```

### 11.4 RAG Chatbot

**Architecture :**
```
Question → Embeddings (SentenceTransformer: paraphrase-multilingual-MiniLM-L12-v2)
    ↓
ChromaDB (similarity search, top-5)
    ↓
Prompt enrichi avec contexte
    ↓
LLM (Gemma 4 via Ollama localhost:11434)
    ↓
Réponse + Sources
```

```python
class RAGService:
    def chat(self, question: str, group_name: str, chat_history: list):
        # 1. Embedding question
        query_embedding = self.embedding_model.encode(question)
        
        # 2. Retrieval ChromaDB
        results = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=5,
            where={"group": group_name}
        )
        
        # 3. Construction contexte
        context = "\n".join(results["documents"][0])
        
        # 4. Prompt engineering
        prompt = f"""Contexte: {context}

Question: {question}

Répondez en français de manière concise et professionnelle."""
        
        # 5. Génération
        response = self.ollama_client.generate(
            model="gemma:4b",
            prompt=prompt
        )
        
        return {
            "answer": response["response"],
            "sources": results["metadatas"][0]
        }
```

---

## 12. WHATSAPP SERVICE

### 12.1 Commandes Supportées

| Commande | Description |
|----------|-------------|
| `/stats` | Stats tickets agence (nouveau/en cours/résolu) |
| `/ticket <ref>` | Détail ticket par numéro |
| `/queue` | File d'attente agence (position, priorité) |
| `/decision <ref>` | Recommandation IA pour ticket |
| `/aide` ou `help` | Liste des commandes |

### 12.2 Voice Message Processing

```javascript
// Transcription audio WhatsApp
async function handleVoiceMessage(chat, msg) {
    const media = await msg.downloadMedia();
    
    // Envoi à ai-service/transcribe
    const transcription = await transcribeAudio(media.data);
    
    // Réponse avec transcription
    await chat.sendMessage(`🎤 Transcription: "${transcription}"`);
}
```

### 12.3 RAG Chat Integration

```javascript
// Chat naturel (pas une commande)
async function handleRAGChat(chat, question, groupName) {
    // Appel ai-service/chat
    const response = await aiServiceChat({
        question,
        group_name: groupName,
        chat_history: getHistory(groupName)
    });
    
    // Réponse + boutons feedback
    await chat.sendMessage(
        `🤖 ${response.answer}\n\n` +
        `Répond avec 👍 ou 👎 pour m'améliorer!`
    );
}
```

### 12.4 Auto Group Creation

```javascript
// Écoute RabbitMQ: agence.created
async function startRabbitMQListener() {
    const queue = 'whatsapp.agence.created';
    await channel.consume(queue, async (msg) => {
        const agence = JSON.parse(msg.content);
        
        // Création groupe WhatsApp
        const group = await client.createGroup(
            `Agence: ${agence.nom}`,
            [agence.telephone]
        );
        
        // Mise à jour ID groupe dans DB
        await updateAgenceWhatsappGroupId(agence.agenceId, group.id);
    });
}
```

---

## 13. INFRASTRUCTURE & COMMUNICATION

### 13.1 RabbitMQ Exchanges & Queues

| Exchange | Routing Key | Queue | Service |
|----------|-------------|-------|---------|
| `user.exchange` | `user.created` | - | organisation → whatsapp |
| `user.exchange` | `agence.created` | `whatsapp.agence.created` | organisation → whatsapp |
| `ticket.exchange` | `ticket.created` | `notification.queue` | ticket → notification |
| `ticket.exchange` | `ticket.resolved` | `notification.queue` | ticket → notification |
| `ticket.exchange` | `ticket.status.changed` | `analytics.queue` | ticket → analytics |
| `decision.exchange` | `decision.finalized` | `notification.queue` | decision → notification |

### 13.2 Bases de Données

| Service | DB | Tables Principales |
|---------|----|-------------------|
| auth-service | auth_db | refresh_tokens |
| organisation-service | org_db | users, agences |
| ticket-service | ticket_db | tickets, ticket_actions |
| rules-service | rules_db | rules, rule_versions, reference_items |
| decision-service | decision_db | decisions |
| notification-service | notif_db | notification_logs |
| analytics-service | analytics_db | daily_stats |
| ai-service | ai_db | feedback_entries, chat_messages |

### 13.3 Configuration Environnement

```bash
# Variables critiques
JWT_SECRET=32-chars-minimum-secret-key
JWT_REFRESH_SECRET=different-32-chars-key
AUDIO_ENCRYPTION_KEY=base64-32-bytes-key
OPENAI_API_KEY=sk-... (optional, fallback rules)
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
RABBITMQ_URL=amqp://guest:guest@localhost:5672
EUREKA_URL=http://localhost:8761/eureka
REDIS_HOST=localhost
OLLAMA_URL=http://localhost:11434
```

### 13.4 Docker Compose Stack

```yaml
services:
  # Infrastructure
  postgres: { image: postgres:15, ports: ["5432:5432"] }
  redis: { image: redis:7-alpine, ports: ["6379:6379"] }
  rabbitmq: { image: rabbitmq:3-management, ports: ["5672:5672", "15672:15672"] }
  
  # Java Services
  eureka-server: { build: ./services/eureka-server, ports: ["8761:8761"] }
  api-gateway: { build: ./services/api-gateway, ports: ["8080:8080"], depends_on: [eureka, redis] }
  auth-service: { build: ./services/auth-service, ports: ["8081:8081"], depends_on: [postgres, eureka] }
  # ... autres services
  
  # AI Service
  ai-service: { 
    build: ./services/ai-service, 
    ports: ["8000:8000"],
    volumes: ["ai-cache:/app/cache"]  # Persist Whisper models
    depends_on: [postgres, chromadb]
  }
```

---

## 📈 STATISTIQUES DU PROJET

| Métrique | Valeur |
|----------|--------|
| **Services** | 11 microservices |
| **Fichiers Java** | 200+ |
| **Fichiers Python** | 20+ |
| **Endpoints REST** | 80+ |
| **Tables DB** | 25+ |
| **Events RabbitMQ** | 8 types |
| **Lines of Code** | ~25,000 |

---

## ✅ CHECKLIST FONCTIONNALITÉS CDC

| ID | Description | Status |
|----|-------------|--------|
| **CDC 1.A** | Opérateur/Superviseur rattaché à agence | ✅ |
| **CDC 1.B** | Responsable agence = SUPERVISOR | ✅ |
| **CDC 1.C** | RBAC Supervisor voit son agence | ✅ |
| **CDC 2.A** | Acquisition multicanale (CALL/EMAIL/WEB) | ✅ |
| **CDC 2.B** | Numéro unique auto-généré | ✅ |
| **CDC 3.A** | File d'attente temps réel | ✅ |
| **CDC 4.A** | CRUD règles avec priorité | ✅ |
| **CDC 4.B** | Listes de référence paramétrables | ✅ |
| **CDC 4.C** | Opérateurs évaluent | ✅ |
| **CDC 4.D** | Versioning automatique | ✅ |
| **CDC 4.E** | Évaluation automatique | ✅ |
| **CDC 5.1** | Consentement RGPD explicite | ✅ |
| **CDC 5.2** | Chiffrement AES-256 audio | ✅ |
| **CDC 5.3** | Anonymisation suppression | ✅ |
| **CDC 5.4** | Détection pièce, sinistre, sentiment | ✅ |
| **CDC 5.5** | Extraction mots-clés transcription | ✅ |

---

*Document généré pour analyse approfondie du projet AutoAssist*
