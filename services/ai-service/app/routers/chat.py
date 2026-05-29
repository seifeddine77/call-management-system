"""
Chat router — RAG-powered chatbot endpoints.

Endpoints:
- POST /chat          → RAG chatbot (question → answer)
- GET  /chat/test     → Quick test from browser (?q=question)
- POST /chat/feedback → Record operator feedback (👍/👎)
- POST /chat/refresh  → Re-index knowledge base at runtime
- GET  /chat/stats    → RAG performance & feedback statistics
- GET  /chat/health   → RAG system health check
"""
import logging
from pydantic import BaseModel, Field
from typing import Annotated
from fastapi import APIRouter, Query

from app.rag import rag_service

logger = logging.getLogger("ai-service.chat")

router = APIRouter(prefix="/chat", tags=["chatbot"])


# ── Request/Response Models ─────────────────────────────────

class ChatRequest(BaseModel):
    """Incoming chat message from WhatsApp."""
    question: str = Field(..., min_length=1, max_length=2000, description="User question")
    group_name: str = Field(default="", description="WhatsApp group name for conversation memory")


class ChatResponse(BaseModel):
    """Chatbot response with metadata."""
    answer: str
    sources: list[str] = []
    documents_retrieved: int = 0
    response_id: str = ""
    response_time_ms: int = 0
    llm_used: str = ""


class FeedbackRequest(BaseModel):
    """Operator feedback on a chatbot response."""
    question: str = Field(..., description="Original question")
    answer: str = Field(..., description="Bot answer that was rated")
    rating: str = Field(..., pattern="^(positive|negative)$", description="positive or negative")
    group_name: str = Field(default="", description="WhatsApp group")


class FeedbackResponse(BaseModel):
    """Feedback recording result."""
    recorded: bool
    total_feedback: int
    satisfaction_rate: str


# ── Endpoints ───────────────────────────────────────────────

@router.post("/", response_model=ChatResponse)
async def chat_endpoint(request: ChatRequest):
    """
    RAG Chatbot endpoint with all 8 improvements + LLM fallback.

    Pipeline:
    1. User question → Hybrid search (semantic + keyword) in ChromaDB
    2. Re-ranking of retrieved documents
    3. Conversation memory context added
    4. Context + question → Gemma 4 (→ OpenAI fallback) → contextual answer
    5. Answer + metadata sent back
    """
    logger.info("💬 Chat question: '%.80s...' group='%s'", request.question, request.group_name)

    result = await rag_service.chat(request.question, request.group_name)

    logger.info(
        f"✅ Response: {len(result['answer'])} chars, "
        f"{result['documents_retrieved']} docs, "
        f"{result['response_time_ms']}ms, "
        f"llm={result.get('llm_used', 'N/A')}"
    )

    return ChatResponse(**result)


@router.get("/test", response_model=ChatResponse)
async def chat_test_endpoint(
    q: Annotated[str, Query(min_length=1, max_length=500, description="Question to test")],
):
    """
    Quick test endpoint — accessible from browser URL bar.

    Usage: GET /chat/test?q=comment créer un ticket

    Same RAG pipeline as POST /chat/, but via GET for easy browser testing.
    """
    logger.info("🧪 Test question: '%.80s...'", q)

    result = await rag_service.chat(q, group_name="browser_test")

    return ChatResponse(**result)


@router.post("/feedback", response_model=FeedbackResponse)
async def feedback_endpoint(request: FeedbackRequest):
    """
    Record operator feedback on a chatbot response.
    Used to track quality and continuously improve the RAG system.

    Rating: 'positive' (👍) or 'negative' (👎)
    """
    result = rag_service.record_feedback(
        question=request.question,
        answer=request.answer,
        rating=request.rating,
        group_name=request.group_name,
    )
    return FeedbackResponse(**result)


@router.post("/refresh")
async def refresh_endpoint():
    """
    Re-index the entire knowledge base without restarting.
    Fetches latest rules from rules-service and re-embeds everything.

    Useful when an admin adds/modifies business rules.
    """
    logger.info("🔄 Manual refresh triggered...")
    result = rag_service.refresh_knowledge_base()
    logger.info(f"✅ Refresh complete: {result['documents_count']} documents")
    return result


@router.get("/stats")
def stats_endpoint():
    """
    Comprehensive RAG statistics:
    - Query performance (count, avg response time)
    - Feedback metrics (satisfaction rate)
    - Knowledge base size
    - Active improvements list
    """
    return rag_service.get_rag_stats()


@router.get("/health")
def chat_health():
    """Check RAG system status."""
    from app.core.config import settings
    return {
        "status": "UP",
        "chromadb_initialized": rag_service._collection is not None,
        "documents_count": rag_service._collection.count() if rag_service._collection else 0,
        "embedding_model": "paraphrase-multilingual-MiniLM-L12-v2",
        "primary_llm": settings.LLM_MODEL,
        "fallback_llm": settings.OPENAI_MODEL if settings.OPENAI_API_KEY else "none",
        "improvements_active": 9,
    }

