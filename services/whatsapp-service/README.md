# WhatsApp Service

**Stack :** Node.js · **Base de données :** aucune (passerelle)

## Rôle
Passerelle d'intégration WhatsApp : relaie les messages entrants (texte/vocaux) vers la plateforme et renvoie les réponses du chatbot RAG aux utilisateurs.

## Points notables (soutenance)
- Canal conversationnel grand public : un client peut interroger le chatbot (procédures, agences, suivi) directement via WhatsApp.
- Les messages **vocaux** sont transmis à l'ai-service (Whisper) ; les réponses proviennent du RAG (`/chat`).
- Les feedbacks 👍/👎 collectés ici alimentent les statistiques affichées dans l'écran **Analytique** (`/api/chat/stats`).

## Dépendances
ai-service (chatbot RAG + transcription), API WhatsApp.
