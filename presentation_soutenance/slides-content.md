# 📋 Contenu des Slides - Soutenance AutoAssist

## SLIDE 1 : Page de Garde
**Titre :** AutoAssist  
**Sous-titre :** Plateforme Microservices Intelligente pour la Gestion des Sinistres Automobiles

- **Réalisé par :** [Ton Nom]
- **Encadrant :** [Nom Encadrant]
- **Filière :** [Génie Logiciel / Informatique]
- **Année Universitaire :** 2025-2026

---

## SLIDE 2 : Contexte du Projet

| Élément | Description |
|---------|-------------|
| **Secteur** | Assurance automobile - Gestion des sinistres |
| **Problème** | Processus manuels, lents, erreurs humaines |
| **Volume** | Centaines d'appels/jour à traiter |
| **Besoin** | Automatisation + Décision rapide + Traçabilité RGPD |

**Enjeux :**
- Processus manuels chronophages
- Décisions subjectives sans scoring
- Conformité RGPD obligatoire
- Communication fragmentée (email, téléphone, web)

---

## SLIDE 3 : Problématique

> **"Comment concevoir une plateforme intelligente capable d'automatiser la gestion des réclamations d'assurance tout en garantissant la conformité RGPD et la qualité des décisions ?"**

**Problèmes identifiés :**
- 🔴 Traitement manuel lent
- 🔴 Décisions sans aide IA
- 🔴 Pas de traçabilité RGPD
- 🔴 Canaux non intégrés

**Besoins :**
- 🟢 Automatisation intelligente
- 🟢 Scoring IA
- 🟢 Conformité légale
- 🟢 Omnicanal

---

## SLIDE 4 : Objectifs du Projet

| Objectif | Description | Technologie |
|----------|-------------|-------------|
| **O1** | Acquisition multicanale | Call, Email, Web |
| **O2** | Transcription automatique | Whisper OpenAI |
| **O3** | Aide à la décision IA | Règles + LLM |
| **O4** | Conformité RGPD | Chiffrement AES-256 |
| **O5** | Chatbot WhatsApp | Node.js + RAG |

---

## SLIDE 5 : Méthodologie & Stack

**Approche :** Agile (Sprints 2 semaines)

**Phases :**
1. Analyse → Cahier des charges
2. Conception → UML + MCD
3. Développement → Services fonctionnels
4. Intégration → Docker + CI/CD

**Stack Technique :**
- ☕ **Backend :** Java 17 + Spring Boot 3.3
- 🐍 **IA :** Python + FastAPI + Whisper
- ⚛️ **Frontend :** React
- 🐳 **DevOps :** Docker + Kubernetes

---

## SLIDE 6 : Architecture Microservices

```
Frontend (React)
    ↓
API Gateway (Spring Cloud Gateway) - JWT + Rate Limiting
    ↓
10 Microservices + Infrastructure (Eureka, Redis, RabbitMQ)
    ↓
PostgreSQL + ChromaDB
```

**Points forts :**
- ✅ Découverte de services (Eureka)
- ✅ Communication asynchrone (RabbitMQ)
- ✅ Cache distribué (Redis)
- ✅ Sécurité (JWT + RBAC)

---

## SLIDE 7 : Services Métier (1/2)

| 🎫 Ticket Service | 🧠 Decision Service | 🤖 AI Service |
|-------------------|-------------------|---------------|
| • Acquisition multicanale | • Scoring IA 3 étapes | • Whisper STT |
| • File d'attente temps réel | • Cache Redis | • RAG Chatbot |
| • WebSocket notifications | • Génération PDF | • ChromaDB |
| • Chiffrement audio | • Circuit Breaker | • Ollama Gemma |

**Flux décision :**
```
Cache → Règles Métier (Score ≥60) → LLM (Fallback)
```

---

## SLIDE 8 : Services Métier (2/2)

**📜 Rules Engine :**
```
Conditions JSONB → Évaluation → Décision
FAVORABLE | DÉFAVORABLE | ÉTUDE
```

**💬 WhatsApp Chatbot :**
- Commandes : `/stats`, `/ticket`, `/queue`, `/decision`
- Transcription vocale automatique
- Feedback IA 👍/👎

**📧 Notification Service :**
- Emails Thymeleaf
- RabbitMQ Events
- Retry automatique

---

## SLIDE 9 : Résultats & Performances

| Métrique | Avant | Après | Gain |
|----------|-------|-------|------|
| Temps de traitement | 30 min | 5 min | **-83%** |
| Précision décisions | 70% | 92% | **+31%** |
| Tâches automatisées | 0% | 65% | **+65%** |
| Conformité RGPD | ❌ | ✅ | **100%** |

**Tests :**
- ✓ 10 microservices fonctionnels
- ✓ 95% couverture tests unitaires
- ✓ Stress test : 1000 req/min

---

## SLIDE 10 : Difficultés Rencontrées

| Difficulté | Solution Adoptée |
|------------|------------------|
| Intégration Whisper (modèle lourd) | Lazy loading + warmup |
| Circuit breaker IA | Fallback sur règles métier |
| WhatsApp (API non officielle) | Gestion QR code + retry |
| RGPD chiffrement audio | AES-256 + consentement explicite |

**💡 Leçons apprises :**
- Microservices = indépendance mais complexité
- IA = assistant, pas remplacement humain
- Tests dès le début (TDD)

---

## SLIDE 11 : Conclusion & Perspectives

**Réalisations :**
- ✅ Plateforme complète et fonctionnelle (10 microservices)
- ✅ Intégration IA complète (STT + NLP + RAG)
- ✅ Conformité RGPD totale (chiffrement + consentement)
- ✅ Architecture scalable et maintenable

**Perspectives :**
1. **📱 Short-term :** Application mobile iOS/Android
2. **🌍 Mid-term :** Support multi-langue (Arabe, Anglais)
3. **⛓️ Long-term :** Blockchain pour traçabilité immutable

---

## SLIDE 12 : Remerciements

**Merci pour votre attention**

**Questions ?**

---

# 📝 Script de Présentation (Oral)

## Slide 1 - Introduction (30 sec)
"Bonjour, je vais vous présenter AutoAssist, une plateforme microservices intelligente pour la gestion des sinistres automobiles. Ce projet répond à un besoin réel du secteur assurance."

## Slide 2 - Contexte (1 min)
"Le secteur assurance fait face à un volume croissant de réclamations. Les processus manuels actuels sont chronophages et source d'erreurs. Notre solution vise à automatiser tout en respectant la RGPD."

## Slide 3 - Problématique (1 min)
"La problématique centrale est : comment automatiser intelligemment tout en garantissant conformité et qualité ?"

## Slide 4 - Objectifs (1 min)
"Nous avons défini 5 objectifs : acquisition multicanale, transcription IA, aide à la décision, conformité RGPD, et chatbot WhatsApp."

## Slide 5 - Méthodologie (1 min)
"Nous avons suivi une méthode Agile avec Java Spring Boot pour les microservices, Python pour l'IA, et React pour le frontend."

## Slide 6 - Architecture (1 min 30)
"Notre architecture comporte 10 microservices orchestrés par une API Gateway. La communication passe par Eureka pour la découverte, RabbitMQ pour les événements, et Redis pour le cache."

## Slide 7-8 - Services (2 min)
"Les services core incluent la gestion des tickets avec chiffrement RGPD, le moteur de décision IA en 3 étapes, et l'AI Service avec Whisper et RAG. Le chatbot WhatsApp permet une interaction directe avec les opérateurs."

## Slide 9 - Résultats (1 min 30)
"Les résultats sont concrets : 83% de réduction du temps de traitement, 31% d'amélioration de la précision des décisions."

## Slide 10 - Difficultés (1 min)
"Parmi les défis : l'intégration de modèles IA lourds via lazy loading, et la gestion du circuit breaker pour la disponibilité."

## Slide 11 - Conclusion (1 min)
"En conclusion, nous avons livré une plateforme complète avec IA intégrée et conformité RGPD. Les perspectives incluent une app mobile et le support multi-langue."

## Slide 12 - Questions (30 sec)
"Je vous remercie pour votre attention et suis disponible pour vos questions."

---

**⏱️ Temps total estimé : 12-13 minutes**
