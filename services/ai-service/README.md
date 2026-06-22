# AI Service (Chatbot RAG, STT & Scoring)

**Port :** `8000` · **Stack :** Python, FastAPI · **Base de données :** `ai_db` (`rag_feedback`)

## Rôle
Brique d'intelligence : transcription vocale, chatbot RAG (WhatsApp) et appui au scoring.

## Endpoints principaux
| Méthode | Chemin | Description |
|---|---|---|
| POST | `/chat/` | Réponse chatbot (RAG + LLM) |
| POST | `/chat/feedback` · GET `/chat/stats` | Feedbacks 👍/👎 et statistiques RAG |
| `/audio/**` | Transcription Whisper + audio chiffré |

## Points notables (soutenance)
- **100 % local — RGPD by design** : LLM **Gemma** via llama.cpp/Docker Model Runner, **Whisper** STT, embeddings multilingues — **aucun appel externe** (OpenAI, etc.).
- RAG avancé : recherche hybride, re-ranking, mémoire de conversation, cache de réponses, feedback loop **persistant** (`rag_feedback`).
- Documentation auto : Swagger `/docs`, ReDoc `/redoc`.

## Dépendances
Modèles locaux (Gemma, Whisper), `ai_db`. Appelé par ticket-service & decision-service (via gateway).
