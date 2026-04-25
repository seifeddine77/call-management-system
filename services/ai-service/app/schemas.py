"""
Pydantic v2 schemas for request/response validation.
"""
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel


# ── Transcription ────────────────────────────────────────────────

class SegmentSchema(BaseModel):
    """A single timestamped segment from Whisper output."""
    start: float
    end: float
    text: str


class TranscribeResponse(BaseModel):
    """Response returned after successful audio transcription."""
    ticket_id: str
    text: str
    confidence: float
    duration_seconds: float
    segments: List[SegmentSchema]


# ── Analysis ─────────────────────────────────────────────────────

class AnalysisRequest(BaseModel):
    """Request body for NLP text analysis."""
    ticket_id: str
    text: str


class AnalysisResponse(BaseModel):
    """Response returned after NLP analysis."""
    ticket_id: str
    piece_reference: Optional[str] = None
    type_sinistre: str
    sentiment: str
    keywords: List[str]
    confidence: float


# ── AudioRecord response ─────────────────────────────────────────

class AudioRecordResponse(BaseModel):
    """Full AudioRecord as returned by GET endpoints."""
    id: str
    ticket_id: str
    file_path: Optional[str] = None
    original_filename: Optional[str] = None
    duration_seconds: Optional[float] = None
    transcription: Optional[str] = None
    confidence: Optional[float] = None
    segments: Optional[List[SegmentSchema]] = None
    status: str
    error_message: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class AnalysisResultResponse(BaseModel):
    """Full AnalysisResult as returned by GET endpoints."""
    id: str
    ticket_id: str
    piece_reference: Optional[str] = None
    type_sinistre: Optional[str] = None
    sentiment: Optional[str] = None
    keywords: Optional[List[str]] = None
    confidence: Optional[float] = None
    raw_text: Optional[str] = None
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}
