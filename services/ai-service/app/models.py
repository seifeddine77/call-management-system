"""
SQLAlchemy ORM models for audio_records and analysis_results tables.
"""
import uuid
from datetime import datetime, timezone

from sqlalchemy import Column, DateTime, Float, JSON, String, Text

from app.core.database import Base


def _utcnow():
    return datetime.now(timezone.utc)


class AudioRecord(Base):
    """Stores audio file metadata, transcription text, and Whisper results."""

    __tablename__ = "audio_records"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    ticket_id = Column(String(36), unique=True, nullable=False, index=True)
    file_path = Column(String(500))
    original_filename = Column(String(255))
    duration_seconds = Column(Float)
    transcription = Column(Text)
    confidence = Column(Float)
    segments = Column(JSON)
    status = Column(String(15), default="PENDING", nullable=False)
    error_message = Column(Text)
    created_at = Column(DateTime, default=_utcnow)
    updated_at = Column(DateTime, default=_utcnow, onupdate=_utcnow)


class AnalysisResult(Base):
    """Stores NLP analysis results: piece reference, sinistre type, sentiment, keywords."""

    __tablename__ = "analysis_results"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    ticket_id = Column(String(36), unique=True, nullable=False, index=True)
    piece_reference = Column(String(100))
    type_sinistre = Column(String(100))
    sentiment = Column(String(20))
    keywords = Column(JSON)
    confidence = Column(Float)
    raw_text = Column(Text)
    created_at = Column(DateTime, default=_utcnow)
