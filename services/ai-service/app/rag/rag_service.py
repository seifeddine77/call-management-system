"""
RAG Service — Retrieval-Augmented Generation for AutoAssist Chatbot.

8 Améliorations implémentées :
1. ✅ Sync dynamique des règles depuis rules-service
2. ✅ Embeddings multilingues (paraphrase-multilingual-MiniLM-L12-v2)
3. ✅ Mémoire de conversation (historique par groupe WhatsApp)
4. ✅ Hybrid Search (sémantique + mots-clés)
5. ✅ Re-ranking (re-classement par pertinence combinée)
6. ✅ Chunking (découpage des longs documents)
7. ✅ Feedback loop (notation 👍/👎 des réponses)
8. ✅ Auto-refresh (endpoint de re-indexation à chaud)
"""
import logging
import re
import time
import asyncio
import httpx
from typing import Optional
from collections import defaultdict, deque
from datetime import datetime

import chromadb
from chromadb.utils import embedding_functions

from app.core.config import settings
from app.rag.knowledge_base import KNOWLEDGE_DOCUMENTS
from app.core.logging_utils import sanitize_log

_CONTENT_TYPE_JSON = "application/json"

# DB imports for persistent feedback
try:
    from sqlalchemy import text as sa_text
    from app.core.database import SessionLocal
    _HAS_DB = True
except Exception:
    _HAS_DB = False

logger = logging.getLogger("ai-service.rag")

RULES_SERVICE_URL = "http://rules-service:8084"
CHUNK_MAX_CHARS = 600  # Max characters per chunk for better retrieval
CONTEXT_MAX_CHARS = 500  # Max chars per doc in LLM context (richer answers)

# ── Global state ────────────────────────────────────────────────
_chroma_client: Optional[chromadb.Client] = None
_collection = None
_embedding_fn = None

# ── Amélioration #3 : Mémoire de conversation par groupe ────────
_conversation_memory: dict[str, deque] = defaultdict(lambda: deque(maxlen=10))

# ── Amélioration #7 : Feedback loop (persistent to DB) ─────────
_feedback_store: list[dict] = []  # In-memory cache (also persisted to DB)

# ── Stats ───────────────────────────────────────────────────────
_stats = {
    "total_queries": 0,
    "avg_response_time": 0.0,
    "total_feedback": 0,
    "positive_feedback": 0,
    "last_refresh": None,
    "cache_hits": 0,
    "intents": defaultdict(int),
    "query_history": deque(maxlen=100),  # Last 100 queries for analytics
}

# ── Response Cache (TTL-based) ──────────────────────────────────
_response_cache: dict[str, dict] = {}  # key → {response, timestamp}
CACHE_TTL_SECONDS = 300  # 5 minutes


def _get_cached_response(question: str) -> Optional[dict]:
    """Check if a similar question was answered recently."""
    # Normalize for cache key
    key = re.sub(r'[^a-zàâéèêëïîôùûüç0-9\s]', '', question.lower().strip())
    key = re.sub(r'\s+', ' ', key).strip()
    
    if key in _response_cache:
        cached = _response_cache[key]
        age = time.time() - cached["timestamp"]
        if age < CACHE_TTL_SECONDS:
            logger.info("⚡ Cache HIT for '%s...' (age: %.0fs)", sanitize_log(question[:40]), age)
            _stats["cache_hits"] += 1
            return cached["response"]
        else:
            del _response_cache[key]  # Expired
    return None


def _cache_response(question: str, response: dict):
    """Cache a response for future identical questions."""
    key = re.sub(r'[^a-zàâéèêëïîôùûüç0-9\s]', '', question.lower().strip())
    key = re.sub(r'\s+', ' ', key).strip()
    _response_cache[key] = {"response": response, "timestamp": time.time()}
    
    # Keep cache size reasonable
    if len(_response_cache) > 50:
        oldest_key = min(_response_cache, key=lambda k: _response_cache[k]["timestamp"])
        del _response_cache[oldest_key]


# INTENT CLASSIFICATION — Route questions optimally

def classify_intent(question: str) -> str:
    """
    Classify user intent to route the question optimally.
    Returns: GREETING, SMALL_TALK, TICKET_QUERY, STATS_QUERY,
             KNOWLEDGE_QUERY, OUT_OF_SCOPE
    """
    lower = question.lower().strip()
    clean = re.sub(r'[!?.,;:]+', '', lower).strip()

    # GREETING
    greetings = ['bonjour', 'bonsoir', 'salut', 'hello', 'hi', 'hey',
                 'salam', 'مرحبا', 'اهلا', 'yo', 'coucou']
    if clean in greetings or any(clean.startswith(g + ' ') for g in greetings):
        return 'GREETING'

    # SMALL_TALK — short thank-yous, affirmations, etc.
    small_talk = ['merci', 'ok', 'oui', 'non', 'd\'accord', 'bien',
                  'super', 'parfait', 'cool', 'compris', 'entendu',
                  'c\'est bon', 'ah ok', 'ah d\'accord', 'شكرا']
    if clean in small_talk or len(clean) < 3:
        return 'SMALL_TALK'

    # TICKET_QUERY — mentions a specific ticket
    if re.search(r'T-\d{8}-\d+', question, re.IGNORECASE):
        return 'TICKET_QUERY'

    # STATS_QUERY — asking for numbers/stats
    stats_kw = ['combien', 'nombre', 'statistique', 'stats', 'total',
                'pourcentage', 'كم', 'عدد']
    if any(k in lower for k in stats_kw):
        return 'STATS_QUERY'

    # OUT_OF_SCOPE — clearly unrelated topics
    out_scope = ['météo', 'meteo', 'football', 'film', 'musique',
                 'restaurant', 'blague', 'politique', 'recette']
    if any(k in lower for k in out_scope):
        return 'OUT_OF_SCOPE'

    # Default: knowledge query → full RAG
    return 'KNOWLEDGE_QUERY'


# LANGUAGE DETECTION — Auto-detect FR/AR/EN

def detect_language(text: str) -> str:
    """
    Detect language of user's question.
    Returns: 'ar' (Arabic), 'fr' (French), 'en' (English)
    """
    # Count Arabic characters (Unicode range 0x0600-0x06FF + 0xFE70-0xFEFF)
    arabic_chars = sum(1 for c in text if '\u0600' <= c <= '\u06FF' or '\uFE70' <= c <= '\uFEFF')
    total_alpha = sum(1 for c in text if c.isalpha())

    if total_alpha == 0:
        return 'fr'

    arabic_ratio = arabic_chars / total_alpha

    if arabic_ratio > 0.3:
        logger.info(f"🌍 Language detected: ARABIC ({arabic_ratio:.0%})")
        return 'ar'

    # Simple English detection
    en_words = ['what', 'how', 'why', 'when', 'where', 'which', 'can', 'could',
                'please', 'the', 'is', 'are', 'do', 'does', 'have', 'has']
    lower = text.lower()
    en_count = sum(1 for w in en_words if f' {w} ' in f' {lower} ')
    if en_count >= 2:
        logger.info(f"🌍 Language detected: ENGLISH")
        return 'en'

    return 'fr'


# QUERY REWRITING — Fix poorly transcribed voice messages

# Common Whisper transcription errors (French)
_WHISPER_FIXES = {
    'bâtures': 'voitures',
    'bature': 'voiture',
    'honnains': 'on a',
    'comme bien': 'combien',
    'réglah': 'règle',
    'tické': 'ticket',
    'agense': 'agence',
    'opérateure': 'opérateur',
    'scoringe': 'scoring',
    'favourables': 'favorables',
    'defavourable': 'défavorable',
    'pise': 'pièce',
    'sinistr': 'sinistre',
    'moteure': 'moteur',
}


def rewrite_query(question: str) -> str:
    """
    Fix common transcription errors from Whisper voice messages.
    Also normalizes the question for better retrieval.
    """
    result = question

    # Apply known fixes (case-insensitive)
    for wrong, right in _WHISPER_FIXES.items():
        pattern = re.compile(re.escape(wrong), re.IGNORECASE)
        result = pattern.sub(right, result)

    # Remove repeated words ("le le", "des des")
    result = re.sub(r'\b(\w+)\s+\1\b', r'\1', result)

    # Clean up extra whitespace
    result = re.sub(r'\s+', ' ', result).strip()

    if result != question:
        logger.info("📝 Query rewritten: '%s' → '%s'", sanitize_log(question[:60]), sanitize_log(result[:60]))

    return result


# Amélioration #6 : CHUNKING — Découper les documents longs

def _chunk_document(doc: dict) -> list[dict]:
    """
    Split a long document into smaller chunks for better retrieval precision.
    Each chunk retains the original metadata for source tracking.
    """
    full_text = f"{doc['title']}. {doc['content']}"

    if len(full_text) <= CHUNK_MAX_CHARS:
        return [doc]

    # Split by sentences
    sentences = re.split(r'(?<=[.!?])\s+', full_text)
    chunks = []
    current_chunk = ""
    chunk_idx = 0

    for sentence in sentences:
        if len(current_chunk) + len(sentence) > CHUNK_MAX_CHARS and current_chunk:
            chunks.append({
                "id": f"{doc['id']}_chunk{chunk_idx}",
                "category": doc["category"],
                "title": doc["title"],
                "content": current_chunk.strip(),
            })
            current_chunk = sentence
            chunk_idx += 1
        else:
            current_chunk += " " + sentence

    if current_chunk.strip():
        chunks.append({
            "id": f"{doc['id']}_chunk{chunk_idx}",
            "category": doc["category"],
            "title": doc["title"],
            "content": current_chunk.strip(),
        })

    return chunks


# Amélioration #1 : SYNC DYNAMIQUE — Règles depuis rules-service

def _fetch_rules_from_service() -> list[dict]:
    """Fetch real business rules from rules-service API."""
    docs = []
    try:
        r = httpx.get(f"{RULES_SERVICE_URL}/api/rules?page=0&size=100&sort=priorite,asc", timeout=10)
        r.raise_for_status()
        rules = r.json().get("content", [])

        for rule in rules:
            if not rule.get("actif", False):
                continue

            conditions_text = _format_conditions(rule.get("conditions", {}))
            doc_content = (
                f"Règle métier : {rule['nom']}. "
                f"Description : {rule.get('description', '')}. "
                f"Conditions : {conditions_text}. "
                f"Décision : {rule['action']}. "
                f"Motif : {rule.get('actionMotif', '')}. "
                f"Priorité : {rule.get('priorite', 'N/A')}."
            )

            docs.append({
                "id": f"rule_{rule['id'][:8]}",
                "category": "regle_dynamique",
                "title": f"Règle : {rule['nom']}",
                "content": doc_content,
            })

        logger.info(f"📏 {len(docs)} règles récupérées depuis rules-service")
    except Exception as e:
        logger.warning(f"⚠️ Impossible de récupérer les règles: {e}")
    return docs


def _fetch_reference_items() -> list[dict]:
    """Fetch reference items from rules-service and group by category."""
    docs = []
    try:
        r = httpx.get(f"{RULES_SERVICE_URL}/api/rules/reference-items", timeout=10)
        r.raise_for_status()
        items = r.json()

        categories = {}
        for item in items:
            cat = item.get("category", "AUTRE")
            if cat not in categories:
                categories[cat] = []
            categories[cat].append(item)

        cat_labels = {
            "TYPE_PIECE": "Types de pièces automobiles (référentiel dynamique)",
            "TYPE_SINISTRE": "Types de sinistres (référentiel dynamique)",
            "CATEGORIE_CLIENT": "Catégories de clients (référentiel dynamique)",
        }

        for cat, cat_items in categories.items():
            items_text = ". ".join(
                f"{it['code']} ({it['label']}) — {it.get('description', '')}"
                for it in cat_items if it.get("actif", True)
            )
            docs.append({
                "id": f"ref_{cat.lower()}",
                "category": "referentiel_dynamique",
                "title": cat_labels.get(cat, f"Référentiel {cat}"),
                "content": f"{cat_labels.get(cat, cat)}. Valeurs : {items_text}",
            })

        logger.info(f"📋 {len(docs)} catégories de référence récupérées")
    except Exception as e:
        logger.warning(f"⚠️ Impossible de récupérer les référentiels: {e}")
    return docs


def _format_conditions(conditions: dict) -> str:
    """Convert JSONB conditions to human-readable French text."""
    if not conditions:
        return "Aucune condition"

    operator = conditions.get("operator", "AND")
    conds = conditions.get("conditions", [])

    op_labels = {
        "EQ": "=", "GT": ">", "LT": "<",
        "GTE": ">=", "LTE": "<=", "NEQ": "!=",
        "CONTAINS": "contient", "IN": "dans",
    }

    parts = []
    for c in conds:
        field = c.get("field", "")
        op = op_labels.get(c.get("op", ""), c.get("op", ""))
        value = c.get("value", "")
        parts.append(f"{field} {op} {value}")

    return f" {operator} ".join(parts)


# INIT + Amélioration #2 : EMBEDDINGS MULTILINGUES

def init_rag():
    """Initialize ChromaDB with multilingual embeddings and index all sources."""
    global _chroma_client, _collection, _embedding_fn

    logger.info("🧠 Initialisation du RAG — ChromaDB + Embeddings multilingues...")

    _chroma_client = chromadb.Client()

    # ── Amélioration #2 : Embeddings multilingues ──
    # paraphrase-multilingual-MiniLM-L12-v2 supporte 50+ langues (FR, AR, EN...)
    # Bien meilleur que all-MiniLM-L6-v2 pour le français
    _embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="paraphrase-multilingual-MiniLM-L12-v2"
    )
    logger.info("🌍 Modèle d'embeddings multilingue chargé")

    # Delete old collection to re-sync
    try:
        _chroma_client.delete_collection("autoassist_knowledge")
    except Exception:
        pass

    _collection = _chroma_client.create_collection(
        name="autoassist_knowledge",
        embedding_function=_embedding_fn,
        metadata={"description": "Base de connaissances AutoAssist — multilingue"},
    )

    _index_all_sources()

    _stats["last_refresh"] = datetime.now().isoformat()
    logger.info("🤖 RAG chatbot initialisé avec toutes les améliorations")


def _index_all_sources():
    """Index all knowledge sources (static + dynamic) with chunking."""
    ids, documents, metadatas = [], [], []

    # ── Source 1: Static knowledge (with chunking) ──
    static_chunks = 0
    for doc in KNOWLEDGE_DOCUMENTS:
        chunks = _chunk_document(doc)
        for chunk in chunks:
            ids.append(chunk["id"])
            documents.append(chunk["content"])
            metadatas.append({"category": chunk["category"], "title": chunk["title"]})
            static_chunks += 1

    logger.info(f"📖 {len(KNOWLEDGE_DOCUMENTS)} docs statiques → {static_chunks} chunks")

    # ── Source 2: Dynamic rules ──
    dynamic_rules = _fetch_rules_from_service()
    for doc in dynamic_rules:
        chunks = _chunk_document(doc)
        for chunk in chunks:
            ids.append(chunk["id"])
            documents.append(chunk["content"])
            metadatas.append({"category": chunk["category"], "title": chunk["title"]})

    # ── Source 3: Dynamic reference items ──
    ref_items = _fetch_reference_items()
    for doc in ref_items:
        chunks = _chunk_document(doc)
        for chunk in chunks:
            ids.append(chunk["id"])
            documents.append(chunk["content"])
            metadatas.append({"category": chunk["category"], "title": chunk["title"]})

    # ── Index everything ──
    _collection.add(ids=ids, documents=documents, metadatas=metadatas)
    total = len(ids)
    logger.info(
        f"✅ {total} chunks indexés dans ChromaDB "
        f"(statiques: {static_chunks}, règles: {len(dynamic_rules)}, réf: {len(ref_items)})"
    )


# Amélioration #8 : AUTO-REFRESH — Re-indexation à chaud

def refresh_knowledge_base() -> dict:
    """
    Re-index the entire knowledge base without restarting the service.
    Called via POST /chat/refresh or when rules change via RabbitMQ.
    """
    global _collection

    logger.info("🔄 Auto-refresh de la base de connaissances...")

    try:
        _chroma_client.delete_collection("autoassist_knowledge")
    except Exception:
        pass

    _collection = _chroma_client.create_collection(
        name="autoassist_knowledge",
        embedding_function=_embedding_fn,
        metadata={"description": "Base de connaissances AutoAssist — multilingue"},
    )

    _index_all_sources()

    _stats["last_refresh"] = datetime.now().isoformat()

    return {
        "status": "refreshed",
        "documents_count": _collection.count(),
        "refreshed_at": _stats["last_refresh"],
    }


# Améliorations #4 + #5 : HYBRID SEARCH + RE-RANKING

def _keyword_score(query: str, document: str) -> float:
    """
    Calculate keyword overlap score between query and document.
    Used for hybrid search (semantic + keyword matching).
    """
    query_words = set(re.findall(r'\w{3,}', query.lower()))
    doc_words = set(re.findall(r'\w{3,}', document.lower()))

    if not query_words:
        return 0.0

    overlap = query_words & doc_words
    return len(overlap) / len(query_words)


def _rerank(docs: list[dict], query: str) -> list[dict]:
    """
    Re-rank retrieved documents using combined scoring:
    - Semantic distance (from ChromaDB) → 70% weight
    - Keyword overlap → 30% weight

    This hybrid approach catches both semantic meaning and exact keyword matches.
    """
    for doc in docs:
        # Normalize semantic distance to 0-1 score (lower distance = higher score)
        semantic_score = max(0, 1 - (doc.get("distance", 1.0) / 2.0))

        # Keyword overlap score
        kw_score = _keyword_score(query, doc.get("content", ""))

        # Combined score (weighted)
        doc["combined_score"] = (semantic_score * 0.7) + (kw_score * 0.3)

    # Sort by combined score (highest first)
    docs.sort(key=lambda d: d["combined_score"], reverse=True)

    return docs


def retrieve(query: str, n_results: int = 6) -> list[dict]:
    """
    Hybrid retrieval: semantic search + keyword re-ranking.

    Steps:
    1. ChromaDB semantic search → top-N candidates
    2. Keyword scoring → hybrid score
    3. Re-ranking → best documents first
    """
    if _collection is None:
        logger.warning("ChromaDB not initialized")
        return []

    # ── Amélioration #4 : Retrieve more, then filter ──
    results = _collection.query(
        query_texts=[query],
        n_results=n_results,
    )

    docs = []
    for i in range(len(results["ids"][0])):
        docs.append({
            "id": results["ids"][0][i],
            "content": results["documents"][0][i],
            "category": results["metadatas"][0][i].get("category", ""),
            "title": results["metadatas"][0][i].get("title", ""),
            "distance": results["distances"][0][i] if results.get("distances") else 1.0,
        })

    # ── Amélioration #5 : Re-rank with hybrid scoring ──
    docs = _rerank(docs, query)

    # Return top 4 after re-ranking
    return docs[:4]


# Amélioration #7 : FEEDBACK LOOP — Notation des réponses

def record_feedback(question: str, answer: str, rating: str, group_name: str = "") -> dict:
    """
    Record operator feedback (👍 positive / 👎 negative) on a chatbot response.
    Persists to PostgreSQL and keeps in-memory cache.
    """
    feedback = {
        "timestamp": datetime.now().isoformat(),
        "question": question,
        "answer_preview": answer[:200],
        "rating": rating,
        "group_name": group_name,
    }
    _feedback_store.append(feedback)

    _stats["total_feedback"] += 1
    if rating == "positive":
        _stats["positive_feedback"] += 1

    satisfaction = (
        (_stats["positive_feedback"] / _stats["total_feedback"] * 100)
        if _stats["total_feedback"] > 0 else 0
    )

    # ── Persist to PostgreSQL ──
    if _HAS_DB:
        try:
            db = SessionLocal()
            db.execute(sa_text(
                "INSERT INTO rag_feedback (question, answer_preview, rating, group_name) "
                "VALUES (:q, :a, :r, :g)"
            ), {"q": question, "a": answer[:200], "r": rating, "g": group_name})
            db.commit()
            db.close()
            logger.info("💾 Feedback persisted to DB")
        except Exception as e:
            logger.warning(f"⚠️ Could not persist feedback to DB: {e}")

    logger.info(
        f"{'👍' if rating == 'positive' else '👎'} Feedback: {rating} "
        f"(satisfaction: {satisfaction:.0f}%)"
    )

    return {
        "recorded": True,
        "total_feedback": _stats["total_feedback"],
        "satisfaction_rate": f"{satisfaction:.0f}%",
    }


def get_feedback_stats() -> dict:
    """Return feedback statistics."""
    satisfaction = (
        (_stats["positive_feedback"] / _stats["total_feedback"] * 100)
        if _stats["total_feedback"] > 0 else 0
    )

    # Try to get count from DB for persistent stats
    db_total = 0
    db_positive = 0
    if _HAS_DB:
        try:
            db = SessionLocal()
            row = db.execute(sa_text(
                "SELECT COUNT(*) as total, "
                "COUNT(*) FILTER (WHERE rating = 'positive') as positive "
                "FROM rag_feedback"
            )).fetchone()
            if row:
                db_total = row[0]
                db_positive = row[1]
            db.close()
        except Exception:
            pass

    return {
        "total_feedback": max(_stats["total_feedback"], db_total),
        "positive": max(_stats["positive_feedback"], db_positive),
        "negative": max(_stats["total_feedback"], db_total) - max(_stats["positive_feedback"], db_positive),
        "satisfaction_rate": f"{satisfaction:.0f}%",
        "recent_feedback": _feedback_store[-10:],
        "db_persisted": db_total,
    }


# Amélioration #3 : MÉMOIRE DE CONVERSATION

def _get_conversation_context(group_name: str) -> str:
    """
    Build conversation context from recent messages in this group.
    Enables multi-turn conversations where the bot remembers previous Q&A.
    """
    if not group_name or group_name not in _conversation_memory:
        return ""

    history = list(_conversation_memory[group_name])
    if not history:
        return ""

    context = "HISTORIQUE :\n"
    for msg in history[-3:]:
        context += f"Q: {msg['question']}\n"
        context += f"R: {msg['answer'][:100]}\n"

    return context


def _resolve_followup(question: str, group_name: str) -> str:
    """
    Contextual Follow-up: Resolve pronouns and references using conversation memory.
    'Et son score ?' → 'Quel est le score de la règle Prise en charge véhicule électrique ?'
    """
    if not group_name or group_name not in _conversation_memory:
        return question

    history = list(_conversation_memory[group_name])
    if not history:
        return question

    lower = question.lower().strip()

    # Detect follow-up patterns (pronouns, short references)
    followup_patterns = [
        r'^et (son|sa|ses|leur|le|la|les)\b',
        r'^(quel|quelle|quels|quelles) (est|sont) (son|sa|ses|leur)\b',
        r'^(c\'est quoi|combien|pourquoi|comment)\s+(son|sa|ses|ça|cela)\b',
        r'^(plus de|encore|autre) (détails?|infos?)\b',
        r'^(et|aussi|également)\s+(pour|avec|dans|sur)\b',
    ]

    is_followup = any(re.search(p, lower) for p in followup_patterns) or len(lower.split()) <= 4

    if is_followup and len(lower) < 60:
        # Get the last topic discussed
        last = history[-1]
        last_question = last['question']

        # Extract key topic from last exchange
        enriched = f"{question} (contexte: {last_question})"
        logger.info("🔗 Follow-up resolved: '%s' → '%s'", sanitize_log(question[:40]), sanitize_log(enriched[:60]))
        return enriched

    return question


def _save_to_memory(group_name: str, question: str, answer: str):
    """Save Q&A pair to conversation memory."""
    if group_name:
        _conversation_memory[group_name].append({
            "question": question,
            "answer": answer,
            "timestamp": datetime.now().isoformat(),
        })


# Serialize LLM calls — Gemma 4 on CPU can only handle one request at a time
_llm_lock = asyncio.Lock()


async def _call_llm(messages: list[dict]) -> tuple[str, str]:
    """
    Call LLM with automatic fallback:
      1. Try local Gemma 4 (free, CPU, ~7-30s)
      2. If timeout/error AND OpenAI key configured → fallback to GPT-4o-mini (~2s)

    Uses asyncio.Lock to prevent concurrent Gemma requests which cause empty responses.
    """
    # ── Attempt 1: Local Gemma 4 (serialized, with retry) ──
    async with _llm_lock:
        for attempt in range(3):
            try:
                logger.info(f"🧠 Gemma 4 attempt {attempt + 1}/3...")
                async with httpx.AsyncClient(timeout=settings.LLM_TIMEOUT) as client:
                    response = await client.post(
                        f"{settings.LLM_BASE_URL}/chat/completions",
                        headers={
                            "Authorization": f"Bearer {settings.LLM_API_KEY}",
                            "Content-Type": _CONTENT_TYPE_JSON,
                        },
                        json={
                            "model": settings.LLM_MODEL,
                            "max_tokens": 1024,
                            "temperature": 0.3,
                            "messages": messages,
                        },
                    )
                    response.raise_for_status()
                    data = response.json()
                    raw = data["choices"][0]["message"]["content"]
                    finish = data["choices"][0].get("finish_reason", "unknown")
                    logger.info(f"📨 Gemma raw: {len(raw)} chars, finish={finish}")

                    text = re.sub(r'```\w*\n?', '', raw).strip() or raw.strip()
                    if text:
                        logger.info(f"✅ Gemma 4 responded ({len(text)} chars)")
                        return text, "gemma4"

                    # Empty response — wait and retry
                    logger.warning(f"⚠️ Gemma 4 empty (finish={finish}), retry in 3s...")
                    await asyncio.sleep(3)

            except httpx.TimeoutException:
                logger.warning(f"⏱️ Gemma 4 timeout after {settings.LLM_TIMEOUT}s")
                break
            except Exception as e:
                logger.warning(f"⚠️ Gemma 4 error: {e}")
                break

    # ── Attempt 2: OpenAI Fallback (outside lock) ──
    if settings.OPENAI_API_KEY:
        try:
            logger.info("🔄 Fallback → OpenAI GPT-4o-mini...")
            async with httpx.AsyncClient(timeout=settings.OPENAI_TIMEOUT) as client:
                response = await client.post(
                    f"{settings.OPENAI_BASE_URL}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {settings.OPENAI_API_KEY}",
                        "Content-Type": _CONTENT_TYPE_JSON,
                    },
                    json={
                        "model": settings.OPENAI_MODEL,
                        "max_tokens": 1024,
                        "temperature": 0.3,
                        "messages": messages,
                    },
                )
                response.raise_for_status()
                data = response.json()
                raw = data["choices"][0]["message"]["content"]
                text = re.sub(r'```\w*\n?', '', raw).strip() or raw.strip()
                if text:
                    logger.info(f"✅ OpenAI fallback responded ({len(text)} chars)")
                    return text, "gpt-4o-mini"

        except Exception as e:
            logger.error(f"❌ OpenAI fallback also failed: {e}")

    return "", "none"


async def warmup_llm():
    """
    Send a tiny request to Gemma 4 at startup to preload the model into RAM.
    This eliminates the ~60s cold-start penalty on the first real query.
    """
    try:
        logger.info("🔥 Warming up Gemma 4 (preloading into RAM)...")
        async with httpx.AsyncClient(timeout=180) as client:
            response = await client.post(
                f"{settings.LLM_BASE_URL}/chat/completions",
                headers={
                    "Authorization": f"Bearer {settings.LLM_API_KEY}",
                    "Content-Type": _CONTENT_TYPE_JSON,
                },
                json={
                    "model": settings.LLM_MODEL,
                    "max_tokens": 5,
                    "temperature": 0,
                    "messages": [{"role": "user", "content": "ok"}],
                },
            )
            response.raise_for_status()
            logger.info("✅ Gemma 4 warmed up — model in RAM, ready for fast inference")
    except Exception as e:
        logger.warning(f"⚠️ Warmup failed (Gemma may be slow on first query): {e}")


# SMART DATA ROUTER — Détecte les questions sur données live
# et enrichit le contexte RAG avec les données temps réel

TICKET_SERVICE_URL = "http://ticket-service:8083"
DECISION_SERVICE_URL = "http://decision-service:8085"
ORGANISATION_SERVICE_URL = "http://organisation-service:8082"

# Patterns that indicate the user wants live data
_STATS_PATTERNS = [
    "combien", "nombre", "statistique", "stats", "total", "pourcentage",
    "en attente", "en cours", "résolu", "fermé", "nouveau",
    "file d'attente", "queue", "كم", "عدد",
]
_TICKET_PATTERNS = [
    "ticket", "T-", "réclamation", "demande", "dossier",
]
_RULES_PATTERNS = [
    "règle", "regle", "condition", "critère", "seuil",
    "kilométrage", "âge véhicule", "catégorie client",
    "moteur de règle", "rules", "scoring",
]
_AGENCE_PATTERNS = [
    "agence", "agences", "bureau", "succursale",
]
_DECISION_PATTERNS = [
    "décision", "decision", "favorable", "defavorable", "défavorable",
    "étude", "etude", "recommandation", "analyse ia",
]
_REFERENCE_PATTERNS = [
    "pièce", "piece", "sinistre", "catégorie", "categorie",
    "référence", "reference", "type de pièce", "type de sinistre",
    "mécanique", "mecanique", "carrosserie", "freinage", "électricité",
    "pare-chocs", "moteur", "embrayage", "alternateur",
]
_USER_PATTERNS = [
    "utilisateur", "opérateur", "operateur", "supervisor",
    "superviseur", "admin", "compte", "équipe", "equipe",
]


async def _fetch_live_context(question: str) -> str:
    """
    Detect if the question is about live data and fetch it from backend APIs.
    Returns a string with live data to prepend to the RAG context, or empty string.
    """
    lower = question.lower()
    live_parts = []

    # ── Stats / Queue ──
    if any(p in lower for p in _STATS_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{TICKET_SERVICE_URL}/api/tickets/stats")
                if r.status_code == 200:
                    stats = r.json()
                    live_parts.append(
                        f"[DONNÉES TEMPS RÉEL — Statistiques tickets] "
                        f"Nouveau: {stats.get('nouveau', 0)}, "
                        f"En cours: {stats.get('enCours', 0)}, "
                        f"Résolu: {stats.get('resolu', 0)}, "
                        f"Fermé: {stats.get('ferme', 0)}, "
                        f"Total: {stats.get('total', 0)}"
                    )
                    logger.info(f"📊 Live stats fetched: {stats.get('total', 0)} tickets")

                # Also fetch canal distribution
                r2 = await client.get(f"{TICKET_SERVICE_URL}/api/tickets?page=0&size=200")
                if r2.status_code == 200:
                    all_tickets = r2.json().get("content", [])
                    canaux = {}
                    for t in all_tickets:
                        canal = t.get("canal", "INCONNU")
                        canaux[canal] = canaux.get(canal, 0) + 1
                    canal_text = ", ".join(f"{k}: {v}" for k, v in canaux.items())
                    live_parts.append(
                        f"[DONNÉES TEMPS RÉEL — Répartition par canal] {canal_text}"
                    )
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch live stats: {e}")

    # ── Ticket detail (T-xxxxxxxx-xxxxx) ──
    import re as _re
    ticket_match = _re.search(r'T-\d{8}-\d+', question, _re.IGNORECASE)
    if ticket_match:
        ticket_ref = ticket_match.group(0)
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{TICKET_SERVICE_URL}/api/tickets?page=0&size=200")
                if r.status_code == 200:
                    data = r.json()
                    tickets = data.get("content", [])
                    t = next((t for t in tickets if t.get("numero") == ticket_ref), None)
                    if t:
                        live_parts.append(
                            f"[DONNÉES TEMPS RÉEL — Ticket {ticket_ref}] "
                            f"Statut: {t.get('statut', '?')}, "
                            f"Canal: {t.get('canal', '?')}, "
                            f"Client: {t.get('clientNom', 'N/A')}, "
                            f"Tél: {t.get('clientTel', 'N/A')}, "
                            f"Priorité: {t.get('priorite', 'N/A')}, "
                            f"Agence: {t.get('agenceNom', 'N/A')}, "
                            f"Description: {(t.get('description', '') or '')[:200]}, "
                            f"Créé: {t.get('createdAt', 'N/A')}"
                        )
                        logger.info(f"🎫 Live ticket fetched: {ticket_ref}")

                        # Also fetch decision for this ticket
                        try:
                            rd = await client.get(
                                f"{DECISION_SERVICE_URL}/api/decisions/ticket/{t.get('id')}"
                            )
                            if rd.status_code == 200:
                                dec = rd.json()
                                live_parts.append(
                                    f"[DONNÉES TEMPS RÉEL — Décision {ticket_ref}] "
                                    f"Décision: {dec.get('decision', '?')}, "
                                    f"Score: {dec.get('score', '?')}/100, "
                                    f"Source: {dec.get('source', '?')}, "
                                    f"Motif: {(dec.get('motif', '') or '')[:150]}"
                                )
                        except Exception:
                            pass
        except Exception as e:
            logger.warning("⚠️ Could not fetch ticket %s: %s", sanitize_log(ticket_ref), sanitize_log(e))

    # ── Rules list ──
    if any(p in lower for p in _RULES_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{RULES_SERVICE_URL}/api/rules?page=0&size=100&sort=priorite,asc")
                if r.status_code == 200:
                    data = r.json()
                    rules = data.get("content", [])
                    rules_text = ""
                    for rule in rules[:10]:
                        conds = rule.get("conditions", {})
                        conds_text = ""
                        if isinstance(conds, dict):
                            for op, conditions_list in conds.items():
                                if isinstance(conditions_list, list):
                                    parts = []
                                    for c in conditions_list:
                                        parts.append(
                                            f"{c.get('field', '?')} {c.get('operator', '?')} {c.get('value', '?')}"
                                        )
                                    conds_text = f" ({op}: {', '.join(parts)})"
                        rules_text += (
                            f"- {rule.get('nom', '?')} (priorité {rule.get('priorite', '?')}, "
                            f"décision: {rule.get('decision', '?')}, score: {rule.get('score', '?')})"
                            f"{conds_text} "
                            f"{rule.get('description', '')} "
                        )
                    if rules_text:
                        live_parts.append(
                            f"[DONNÉES TEMPS RÉEL — {len(rules)} règles actives] {rules_text}"
                        )
                        logger.info(f"📏 Live rules fetched: {len(rules)} rules")
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch live rules: {e}")

    # ── Reference data (pièces, sinistres, catégories) ──
    if any(p in lower for p in _REFERENCE_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{RULES_SERVICE_URL}/api/reference-items?page=0&size=100")
                if r.status_code == 200:
                    data = r.json()
                    items = data.get("content", []) if isinstance(data, dict) else data
                    if items:
                        grouped = {}
                        for item in items:
                            cat = item.get("category", item.get("type", "AUTRE"))
                            if cat not in grouped:
                                grouped[cat] = []
                            grouped[cat].append(item.get("label", item.get("nom", "?")))
                        ref_text = "; ".join(
                            f"{cat}: {', '.join(labels[:8])}" for cat, labels in grouped.items()
                        )
                        live_parts.append(
                            f"[DONNÉES TEMPS RÉEL — {len(items)} items de référence] {ref_text}"
                        )
                        logger.info(f"📦 Live reference data fetched: {len(items)} items")
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch reference data: {e}")

    # ── Agences ──
    if any(p in lower for p in _AGENCE_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{ORGANISATION_SERVICE_URL}/api/agences?page=0&size=100")
                if r.status_code == 200:
                    data = r.json()
                    agences = data.get("content", [])
                    agences_text = "; ".join(
                        f"{a.get('nom', '?')} (📍{a.get('adresse', 'N/A')}, "
                        f"📱{a.get('telephone', 'N/A')}, "
                        f"👤 Resp: {a.get('responsableName', 'Non assigné')}, "
                        f"{'✅ Active' if a.get('actif') else '❌ Inactive'})"
                        for a in agences
                    )
                    live_parts.append(
                        f"[DONNÉES TEMPS RÉEL — {len(agences)} agences] {agences_text}"
                    )
                    logger.info(f"🏢 Live agences fetched: {len(agences)} agences")
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch agences: {e}")

    # ── Decisions stats ──
    if any(p in lower for p in _DECISION_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{DECISION_SERVICE_URL}/api/decisions?page=0&size=200")
                if r.status_code == 200:
                    data = r.json()
                    decisions = data.get("content", []) if isinstance(data, dict) else data
                    if decisions:
                        dec_counts = {}
                        source_counts = {}
                        for d in decisions:
                            dec_type = d.get("decision", "INCONNU")
                            dec_counts[dec_type] = dec_counts.get(dec_type, 0) + 1
                            src = d.get("source", "INCONNU")
                            source_counts[src] = source_counts.get(src, 0) + 1
                        dec_text = ", ".join(f"{k}: {v}" for k, v in dec_counts.items())
                        src_text = ", ".join(f"{k}: {v}" for k, v in source_counts.items())
                        live_parts.append(
                            f"[DONNÉES TEMPS RÉEL — {len(decisions)} décisions] "
                            f"Répartition: {dec_text}. Sources: {src_text}"
                        )
                        logger.info(f"⚖️ Live decisions fetched: {len(decisions)} decisions")
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch decisions: {e}")

    # ── Users / équipe ──
    if any(p in lower for p in _USER_PATTERNS):
        try:
            async with httpx.AsyncClient(timeout=8) as client:
                r = await client.get(f"{ORGANISATION_SERVICE_URL}/api/users?page=0&size=100")
                if r.status_code == 200:
                    data = r.json()
                    users = data.get("content", []) if isinstance(data, dict) else data
                    if users:
                        role_counts = {}
                        for u in users:
                            role = u.get("role", "INCONNU")
                            role_counts[role] = role_counts.get(role, 0) + 1
                        role_text = ", ".join(f"{k}: {v}" for k, v in role_counts.items())
                        live_parts.append(
                            f"[DONNÉES TEMPS RÉEL — {len(users)} utilisateurs] "
                            f"Répartition par rôle: {role_text}"
                        )
                        logger.info(f"👥 Live users fetched: {len(users)} users")
        except Exception as e:
            logger.warning(f"⚠️ Could not fetch users: {e}")

    return "\n".join(live_parts)


# MAIN CHAT — Pipeline RAG complet avec toutes les améliorations

async def chat(question: str, group_name: str = "") -> dict:
    """
    RAG pipeline with all improvements + Smart Data Router:
    1. Hybrid retrieval (semantic + keyword)
    2. Multilingual embeddings
    3. Conversation memory
    4. Re-ranking
    5. Chunked documents
    6. Feedback tracking
    7. Dynamic knowledge
    8. Auto-refreshable
    9. LLM fallback (Gemma 4 → OpenAI)
    10. ✨ Smart Data Router (live API data)

    Args:
        question: User question from WhatsApp
        group_name: WhatsApp group name for memory context

    Returns:
        dict with 'answer', 'sources', 'documents_retrieved', 'response_id'
    """
    start_time = time.time()
    _stats["total_queries"] += 1

    # ── Step 0: Intent Classification ──
    intent = classify_intent(question)
    _stats["intents"][intent] += 1
    logger.info(f"🎯 Intent: {intent} for '{question[:50]}...'")

    # Track query for analytics
    _stats["query_history"].append({
        "question": question[:100],
        "intent": intent,
        "group": group_name,
        "timestamp": datetime.now().isoformat(),
    })

    # Handle intents that don't need RAG
    if intent == 'SMALL_TALK':
        lang = detect_language(question)
        if lang == 'ar':
            msg = "👍 حسنًا! لا تتردد في طرح أسئلة أخرى. اكتب */aide* لمشاهدة الأوامر المتاحة. 😊"
        elif lang == 'en':
            msg = "👍 Got it! Feel free to ask more questions. Type */aide* to see available commands. 😊"
        else:
            msg = "👍 D'accord ! N'hésitez pas si vous avez d'autres questions. Tapez */aide* pour voir les commandes disponibles. 😊"
        return {
            "answer": msg,
            "sources": [],
            "documents_retrieved": 0,
            "response_id": f"resp_{int(time.time())}_{_stats['total_queries']}",
            "response_time_ms": int((time.time() - start_time) * 1000),
            "llm_used": "intent_classifier",
        }

    if intent == 'OUT_OF_SCOPE':
        return {
            "answer": "🚗 Je suis *AutoAssist*, spécialisé dans la gestion des réclamations pièces auto.\n\nJe ne peux pas répondre à cette question, mais je peux vous aider avec :\n• 📊 Les statistiques → */stats*\n• 🎫 Un ticket → */ticket T-xxx*\n• 📋 La file d'attente → */queue*\n• ❓ Toute question sur la plateforme !\n\nTapez */aide* pour plus d'infos. 😊",
            "sources": [],
            "documents_retrieved": 0,
            "response_id": f"resp_{int(time.time())}_{_stats['total_queries']}",
            "response_time_ms": int((time.time() - start_time) * 1000),
            "llm_used": "intent_classifier",
        }

    # ── Step 0b: Query Rewriting (fix Whisper errors) ──
    rewritten = rewrite_query(question)

    # ── Step 0c: Contextual Follow-up (resolve pronouns) ──
    rewritten = _resolve_followup(rewritten, group_name)

    # ── Step 0d: Response Cache check ──
    cached = _get_cached_response(rewritten)
    if cached:
        cached_copy = dict(cached)
        cached_copy["response_time_ms"] = int((time.time() - start_time) * 1000)
        cached_copy["llm_used"] = "cache"
        return cached_copy

    # ── Step 1: Hybrid retrieval with re-ranking ──
    relevant_docs = retrieve(rewritten, n_results=5)

    # ── Step 1b: Smart Data Router — fetch live data if needed ──
    live_context = await _fetch_live_context(rewritten)

    # ── Build context — cap size to prevent Gemma "finish=length" with 0 chars ──
    context_parts = []
    sources = []

    if live_context:
        # Live data takes priority — reduce KB docs to avoid exceeding context window
        context_parts.append(live_context)
        sources.append("📊 Données temps réel")
        for doc in relevant_docs[:2]:
            content = doc['content'][:250]
            context_parts.append(f"[{doc['title']}] {content}")
            sources.append(doc["title"])
    else:
        for doc in relevant_docs[:3]:
            content = doc['content'][:CONTEXT_MAX_CHARS]
            context_parts.append(f"[{doc['title']}] {content}")
            sources.append(doc["title"])

    context = "\n".join(context_parts)

    # Hard cap — prevent context overflow that causes empty Gemma responses
    if len(context) > 3000:
        context = context[:3000]

    # ── Step 2: Add conversation memory ──
    conversation_context = _get_conversation_context(group_name)

    # ── Step 3: Build prompt ──
    # NOTE: Docker Model Runner (llama.cpp) handles long system prompts
    # poorly — returns empty content. We merge everything into the user
    # message instead for reliable generation.

    # Detect language for adaptive response
    lang = detect_language(question)

    if lang == 'ar':
        instructions = (
            "أنت AutoAssist، المساعد الذكي لإدارة شكاوى قطع غيار السيارات. "
            "أجب بالعربية بطريقة واضحة ومهنية. "
            "استخدم المعلومات من السياق أدناه فقط. "
            "استخدم *خط عريض* للمصطلحات المهمة وأضف رموز تعبيرية مناسبة. "
            "لا تخترع معلومات أبدًا."
        )
    elif lang == 'en':
        instructions = (
            "You are AutoAssist, the AI assistant for auto parts claims management. "
            "Reply in English clearly and professionally. "
            "Use ONLY information from the CONTEXT below. "
            "Use *bold* for key terms and add relevant emojis. "
            "Never invent information."
        )
    else:
        instructions = (
            "Tu es AutoAssist, l'assistant IA intelligent pour la gestion des réclamations pièces automobiles. "
            "Tu aides les agents des agences à comprendre les procédures, les règles métier et le fonctionnement du système. "
            "RÈGLES : "
            "1. Réponds UNIQUEMENT avec les informations du CONTEXTE ci-dessous. "
            "2. Réponds en français de manière claire, structurée et professionnelle (4-8 phrases). "
            "3. Utilise le format WhatsApp : *gras* pour les termes importants, _italique_ pour les détails. "
            "4. Ajoute des emojis pertinents (📋📊✅❌⚠️🔍) pour rendre la réponse visuelle. "
            "5. Si DONNÉES TEMPS RÉEL sont fournies, cite les chiffres exacts avec contexte. "
            "6. N'invente JAMAIS d'information. Si tu ne sais pas, dis-le honnêtement et suggère une commande (/aide). "
            "7. Les tickets sont créés AUTOMATIQUEMENT via 3 canaux (CALL téléphone, EMAIL, WEB formulaire). "
            "8. Adapte ton ton : professionnel mais chaleureux, comme un collègue expert qui aide."
        )

    user_prompt = f"{instructions}\n\n"

    if conversation_context:
        user_prompt += f"{conversation_context}\n\n"

    user_prompt += f"CONTEXTE :\n{context}\n\nQUESTION : {question}"

    # Final safety cap on total prompt
    if len(user_prompt) > 4500:
        user_prompt = user_prompt[:4500]

    # ── Step 4: Call LLM (Gemma 4 → OpenAI fallback) ──
    messages = [
        {"role": "user", "content": user_prompt},
    ]

    answer, llm_used = await _call_llm(messages)

    if not answer:
        # Both LLMs failed — return context-based fallback
        answer = (
            "📚 Voici les informations pertinentes trouvées :\n\n"
            + "\n".join(f"• *{doc['title']}*" for doc in relevant_docs[:3])
            + "\n\nL'IA est temporairement lente. "
            "Utilisez */stats*, */ticket T-xxx* ou */aide* en attendant."
        )
        llm_used = "fallback_docs"

    # ── Save to conversation memory ──
    _save_to_memory(group_name, question, answer)

    # ── Update stats ──
    elapsed = time.time() - start_time
    _stats["avg_response_time"] = (
        (_stats["avg_response_time"] * (_stats["total_queries"] - 1) + elapsed)
        / _stats["total_queries"]
    )

    response_id = f"resp_{int(time.time())}_{_stats['total_queries']}"

    logger.info(
        f"✅ RAG [{response_id}] '{question[:50]}...' → "
        f"{len(answer)} chars, {len(sources)} sources, {elapsed:.1f}s, llm={llm_used}"
    )

    result = {
        "answer": answer,
        "sources": sources,
        "documents_retrieved": len(relevant_docs),
        "response_id": response_id,
        "response_time_ms": int(elapsed * 1000),
        "llm_used": llm_used,
    }

    # ── Save to response cache (skip live data queries — they change too fast) ──
    if not live_context:
        _cache_response(rewritten, result)

    return result


def get_rag_stats() -> dict:
    """Return comprehensive RAG statistics with analytics."""
    # Top questions (most asked)
    question_counts = defaultdict(int)
    for q in _stats["query_history"]:
        question_counts[q["question"][:60]] += 1
    top_questions = sorted(question_counts.items(), key=lambda x: -x[1])[:10]

    return {
        "total_queries": _stats["total_queries"],
        "avg_response_time_ms": int(_stats["avg_response_time"] * 1000),
        "documents_count": _collection.count() if _collection else 0,
        "last_refresh": _stats["last_refresh"],
        "conversation_groups": len(_conversation_memory),
        "feedback": get_feedback_stats(),
        "embedding_model": "paraphrase-multilingual-MiniLM-L12-v2",
        "cache": {
            "hits": _stats["cache_hits"],
            "size": len(_response_cache),
            "ttl_seconds": CACHE_TTL_SECONDS,
        },
        "intents": dict(_stats["intents"]),
        "top_questions": [{"question": q, "count": c} for q, c in top_questions],
        "improvements": [
            "sync_dynamique", "embeddings_multilingues", "memoire_conversation",
            "hybrid_search", "re_ranking", "chunking", "feedback_loop_persistent",
            "auto_refresh", "llm_fallback", "warmup", "context_optimization",
            "smart_data_router", "voice_messages_whisper", "intent_classification",
            "query_rewriting", "contextual_followup", "response_cache",
            "persistent_feedback_db",
        ],
        "total_improvements": 18,
    }
