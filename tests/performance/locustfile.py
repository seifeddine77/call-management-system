# -*- coding: utf-8 -*-
"""
Test de performance / charge — AutoAssist (Locust)
===================================================

Outil : Locust (https://locust.io) — interface web temps réel sur http://localhost:8089

Cibles NON DESTRUCTIVES (aucune écriture en base) :
  - Chatbot RAG (ai-service)      → exerce le pipeline complet RAG + Gemma 4
  - Statistiques tickets (lecture) → endpoint rapide, fort débit
  - Santé décision (lecture)       → endpoint léger du decision-service

On NE charge PAS POST /api/decisions/analyze : chaque appel crée un
enregistrement en base. Les temps de décision (règles/IA/cache) sont
mesurés séparément par un protocole contrôlé.

Lancement (conteneur Docker, sur le réseau du projet) :
  docker run --rm -p 8089:8089 \
    -v "${PWD}/tests/performance:/mnt/locust" \
    --network call-management-system_callmgmt-net \
    locustio/locust -f /mnt/locust/locustfile.py

Puis ouvrir http://localhost:8089, régler le nombre d'utilisateurs et observer.
"""
import random
from locust import HttpUser, task, between

# URLs internes (résolues par le DNS Docker sur le réseau callmgmt-net)
AI_SERVICE = "http://ai-service:8000"
TICKET_SERVICE = "http://ticket-service:8083"
DECISION_SERVICE = "http://decision-service:8085"

# Questions réalistes posées par les agents (FR)
QUESTIONS = [
    "Quels sont les statuts d'un ticket ?",
    "Comment fonctionne le moteur de regles ?",
    "Quels sont les types de sinistres ?",
    "Comment est cree un ticket automatiquement ?",
    "C'est quoi une decision favorable ?",
    "Quelles sont les categories de clients ?",
]


class AgentWhatsApp(HttpUser):
    """Simule un agent qui interroge le chatbot et consulte des données."""

    # Temps d'attente entre deux actions d'un même utilisateur (réaliste)
    wait_time = between(1, 3)
    host = AI_SERVICE  # host par défaut (requis par Locust)

    @task(1)
    def poser_question_chatbot(self):
        """Voie lourde : RAG + Gemma 4 (sérialisé côté serveur)."""
        q = random.choice(QUESTIONS)
        # name= regroupe toutes les questions sous une seule ligne de stats
        self.client.get(
            f"{AI_SERVICE}/chat/test",
            params={"q": q},
            name="Chatbot RAG /chat/test",
            timeout=120,
        )

    @task(5)
    def consulter_stats_tickets(self):
        """Voie légère : lecture seule, fort débit attendu."""
        self.client.get(
            f"{TICKET_SERVICE}/api/tickets/stats",
            name="Stats tickets (lecture)",
            timeout=30,
        )

    @task(2)
    def sante_decision(self):
        """Endpoint léger du decision-service (lecture)."""
        self.client.get(
            f"{DECISION_SERVICE}/api/decisions/health",
            name="Sante decision-service",
            timeout=30,
        )
