"""
Pytest configuration and fixtures for AI Service tests.
Uses SQLite in-memory database and mocks for Whisper/spaCy.
"""
import os
import sys
from contextlib import asynccontextmanager

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

# Ensure the app package is importable
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# ── Test database (SQLite in-memory) ─────────────────────────────

TEST_DATABASE_URL = "sqlite://"

test_engine = create_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


# ── Patch database BEFORE importing app ──────────────────────────
import app.core.database as db_module

db_module.engine = test_engine
db_module.SessionLocal = TestingSessionLocal

from app.core.database import Base, get_db
from app.models import AudioRecord, AnalysisResult  # noqa: F401 — ensure models loaded


def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


# ── Mock Whisper model ───────────────────────────────────────────

class MockWhisperModel:
    """Mock Whisper model that returns fixed transcription results."""

    def transcribe(self, file_path, language="fr", fp16=False):
        return {
            "text": "Bonjour, mon alternateur est en panne depuis hier matin.",
            "segments": [
                {
                    "start": 0.0,
                    "end": 2.5,
                    "text": " Bonjour, mon alternateur",
                    "avg_logprob": -0.3,
                },
                {
                    "start": 2.5,
                    "end": 5.0,
                    "text": " est en panne depuis hier matin.",
                    "avg_logprob": -0.25,
                },
            ],
        }


# ── Mock spaCy NLP ───────────────────────────────────────────────

class MockToken:
    def __init__(self, text, pos, is_stop=False, lemma=None):
        self.text = text
        self.pos_ = pos
        self.is_stop = is_stop
        self.lemma_ = lemma or text


class MockEntity:
    def __init__(self, text, label):
        self.text = text
        self.label_ = label


class MockDoc:
    def __init__(self, text):
        self.ents = [MockEntity("Renault", "ORG")]
        self.tokens = [
            MockToken("alternateur", "NOUN", lemma="alternateur"),
            MockToken("panne", "NOUN", lemma="panne"),
            MockToken("véhicule", "NOUN", lemma="véhicule"),
            MockToken("le", "DET", is_stop=True),
        ]

    def __iter__(self):
        return iter(self.tokens)


class MockNLP:
    """Mock spaCy model."""

    def __call__(self, text):
        return MockDoc(text)


# ── Build test app with mock lifespan ────────────────────────────

def _create_test_app(whisper_model, nlp_model):
    """Create a fresh FastAPI app with a mock lifespan that uses provided models."""
    from app.routers import analysis, transcription

    @asynccontextmanager
    async def test_lifespan(application: FastAPI):
        application.state.whisper_model = whisper_model
        application.state.nlp = nlp_model
        Base.metadata.create_all(bind=test_engine)
        yield

    test_app = FastAPI(lifespan=test_lifespan)
    test_app.include_router(transcription.router)
    test_app.include_router(analysis.router)
    test_app.dependency_overrides[get_db] = override_get_db

    # Health check (mirrors main.py)
    @test_app.get("/health", tags=["health"])
    def health_check():
        return {
            "status": "UP",
            "service": "ai-service",
            "whisper_loaded": test_app.state.whisper_model is not None,
            "spacy_loaded": test_app.state.nlp is not None,
        }

    return test_app


# ── Fixtures ─────────────────────────────────────────────────────

@pytest.fixture(scope="function", autouse=True)
def setup_db():
    """Create tables before each test, drop after."""
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)


@pytest.fixture()
def client():
    """FastAPI test client with mocked Whisper and spaCy."""
    test_app = _create_test_app(MockWhisperModel(), MockNLP())
    with TestClient(test_app) as c:
        yield c


@pytest.fixture()
def client_no_whisper():
    """FastAPI test client without Whisper model (None)."""
    test_app = _create_test_app(None, MockNLP())
    with TestClient(test_app) as c:
        yield c
