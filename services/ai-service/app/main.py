"""
FastAPI application entry point.
Loads Whisper model and spaCy NLP at startup via lifespan.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from py_eureka_client import eureka_client

from app.core.database import Base, engine
from app.routers import analysis, chat, streaming, transcription
from app.rag import rag_service
from app.core.config import settings

logger = logging.getLogger("ai-service")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s - %(message)s")


@asynccontextmanager
async def lifespan(application: FastAPI):
    """Load heavy ML models and register with Eureka at startup."""
    # Register with Eureka
    logger.info(f"Registering with Eureka at {settings.EUREKA_SERVER}...")
    try:
        await eureka_client.init_async(
            eureka_server=settings.EUREKA_SERVER,
            app_name=settings.APP_NAME,
            instance_port=settings.INSTANCE_PORT
        )
        logger.info(f"Successfully registered as '{settings.APP_NAME}' on port {settings.INSTANCE_PORT}")
    except Exception as e:
        logger.error(f"Failed to register with Eureka: {e}")

    logger.info("Loading Whisper model...")
    try:
        import whisper
        application.state.whisper_model = whisper.load_model(settings.WHISPER_MODEL)
        logger.info(f"Whisper model '{settings.WHISPER_MODEL}' loaded successfully")
    except Exception as e:
        logger.warning(f"Could not load Whisper model: {e}. Transcription will be unavailable.")
        application.state.whisper_model = None

    logger.info("Loading spaCy NLP model...")
    try:
        import spacy
        application.state.nlp = spacy.load("fr_core_news_sm")
        logger.info("spaCy fr_core_news_sm loaded successfully")
    except Exception as e:
        logger.warning(f"Could not load spaCy model: {e}. Keywords extraction will be limited.")
        application.state.nlp = None

    # Create tables if they don't exist
    Base.metadata.create_all(bind=engine)
    logger.info("Database tables ensured")

    # Initialize RAG (ChromaDB + knowledge base)
    logger.info("Initializing RAG chatbot...")
    try:
        rag_service.init_rag()
        logger.info("🤖 RAG chatbot ready")
    except Exception as e:
        logger.warning(f"Could not initialize RAG: {e}. Chatbot will be unavailable.")

    # Warmup Gemma 4 — preload into RAM to avoid cold-start lag
    try:
        await rag_service.warmup_llm()
    except Exception as e:
        logger.warning(f"LLM warmup skipped: {e}")

    yield  # Application runs here

    # Deregister from Eureka
    logger.info("Deregistering from Eureka...")
    try:
        await eureka_client.stop_async()
        logger.info("Successfully deregistered from Eureka")
    except Exception as e:
        logger.error(f"Failed to deregister from Eureka: {e}")

    logger.info("AI Service shutting down")


app = FastAPI(
    title="AI Service — Call Management System",
    description="Whisper STT transcription + NLP analysis for insurance call center",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS — allow all origins in dev
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(transcription.router)
app.include_router(analysis.router)
app.include_router(streaming.router)
app.include_router(chat.router)

# Instrument for Prometheus metrics
from prometheus_fastapi_instrumentator import Instrumentator
Instrumentator().instrument(app).expose(app)



# ── Web Softphone ──────────────────────────────────────────────
@app.get("/softphone", tags=["softphone"])
async def softphone_page():
    """Serve the Web Softphone page."""
    import os
    path = os.path.join(os.path.dirname(__file__), "..", "static", "softphone.html")
    if os.path.exists(path):
        return FileResponse(path, media_type="text/html")
    return {"error": "softphone.html not found"}


# Proxy: forward call ticket creation to ticket-service
import httpx as _httpx
from typing import Annotated
from fastapi import Request, UploadFile, File

@app.post("/api/channels/call", tags=["softphone"])
async def proxy_create_call_ticket(request: Request):
    """Proxy /api/channels/call to ticket-service so the softphone works without CORS."""
    body = await request.json()
    async with _httpx.AsyncClient(timeout=15) as client:
        r = await client.post("http://ticket-service:8083/api/channels/call", json=body)
        return r.json()

@app.post("/api/tickets/{ticket_id}/audio", tags=["softphone"])
async def proxy_upload_audio(ticket_id: str, file: Annotated[UploadFile, File(...)]):
    """
    Proxy audio upload to ticket-service.
    This triggers the FULL pipeline: Whisper → NLP → save transcription to ticket.
    """
    import logging
    logger = logging.getLogger("ai-service.softphone")
    logger.info(f"📞 Softphone: uploading audio to ticket {ticket_id}")

    audio_bytes = await file.read()

    async with _httpx.AsyncClient(timeout=120) as client:
        # Send as multipart/form-data to ticket-service
        files = {"file": (file.filename or "call.webm", audio_bytes, file.content_type or "audio/webm")}
        r = await client.post(
            f"http://ticket-service:8083/api/tickets/{ticket_id}/audio",
            files=files,
            headers={"X-User-Id": "00000000-0000-0000-0000-000000000000", "X-User-Email": "SYSTEM-SOFTPHONE"},
        )
        logger.info(f"✅ Softphone: audio pipeline complete for ticket {ticket_id} (status={r.status_code})")
        return r.json()

@app.get("/api/agences", tags=["softphone"])
async def proxy_get_agences():
    """Proxy /api/agences to organisation-service."""
    async with _httpx.AsyncClient(timeout=10) as client:
        r = await client.get("http://organisation-service:8082/api/agences")
        return r.json()


@app.get("/health", tags=["health"])
def health_check():
    """Health check endpoint."""
    from app.rag.rag_service import _collection
    return {
        "status": "UP",
        "service": "ai-service",
        "whisper_loaded": app.state.whisper_model is not None,
        "spacy_loaded": app.state.nlp is not None,
        "rag_ready": _collection is not None,
        "rag_documents": _collection.count() if _collection else 0,
    }
