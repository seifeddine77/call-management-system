"""
Transcription router — Whisper STT.

POST /transcribe       → upload audio → transcribe → return TranscribeResponse
GET  /transcribe/{tid} → retrieve existing transcription
"""
import logging
import os
import re
import uuid
from typing import Annotated

import aiofiles
from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.crypto import AudioCrypto
from app.core.database import get_db
from app.models import AudioRecord
from app.schemas import AudioRecordResponse, TranscribeResponse
from app.core.logging_utils import sanitize_log

logger = logging.getLogger("ai-service.transcription")

audio_crypto = AudioCrypto(settings.AUDIO_ENCRYPTION_KEY)


router = APIRouter(prefix="/transcribe", tags=["transcription"])

ALLOWED_CONTENT_TYPES = {
    "audio/wav", "audio/x-wav", "audio/mpeg", "audio/ogg",
    "audio/mp4", "audio/webm", "audio/flac",
    "audio/x-m4a", "audio/m4a", "audio/aac",
    "application/octet-stream",  # Feign client fallback
}
ALLOWED_EXTENSIONS = {".wav", ".mp3", ".ogg", ".mp4", ".webm", ".flac", ".m4a", ".aac"}
MAX_SIZE_BYTES = settings.MAX_AUDIO_SIZE_MB * 1024 * 1024


@router.post("/", response_model=TranscribeResponse)
async def transcribe_audio(
    request: Request,
    file: Annotated[UploadFile, File(...)],
    ticket_id: Annotated[str, Form(...)],
    db: Annotated[Session, Depends(get_db)],
):
    """
    Upload an audio file, transcribe it with Whisper, and store the result.
    """
    # ── Validation ──────────────────────────────────────────────
    ext = os.path.splitext(file.filename or "audio.wav")[1].lower()
    content_type = file.content_type or "application/octet-stream"

    # Accept if content-type is known OR file extension is valid
    if content_type not in ALLOWED_CONTENT_TYPES and ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"Type de fichier non supporté: {content_type} (ext={ext}). "
                   f"Formats acceptés: wav, mp3, ogg, mp4, webm, flac",
        )

    # Read file content and check size
    content = await file.read()
    if len(content) > MAX_SIZE_BYTES:
        raise HTTPException(
            status_code=400,
            detail=f"Fichier trop volumineux ({len(content) // (1024*1024)} MB). "
                   f"Maximum: {settings.MAX_AUDIO_SIZE_MB} MB",
        )

    # Check Whisper model availability BEFORE saving file
    whisper_model = request.app.state.whisper_model
    if whisper_model is None:
        raise HTTPException(
            status_code=503,
            detail="Le modèle Whisper n'est pas chargé. Transcription indisponible.",
        )

    # ── Save file to disk ───────────────────────────────────────
    # Prevent path traversal (SonarCloud Blocker S2083) by resolving absolute paths
    base_dir = os.path.abspath(settings.AUDIO_STORAGE_PATH)
    ticket_dir = os.path.abspath(os.path.join(base_dir, ticket_id))
    if not ticket_dir.startswith(base_dir):
        raise HTTPException(status_code=400, detail="Invalid path traversal detected")

    os.makedirs(ticket_dir, exist_ok=True)

    ext = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"
    saved_filename = f"{uuid.uuid4()}{ext}"
    file_path = os.path.join(ticket_dir, saved_filename)

    # Check if content is already encrypted
    is_encrypted = False
    if audio_crypto.is_enabled():
        try:
            # Try to decrypt. If it succeeds, it's already encrypted!
            _ = audio_crypto.decrypt(content)
            is_encrypted = True
            logger.info("Incoming audio file is already encrypted.")
        except Exception:
            # It's plaintext
            is_encrypted = False

    # If key is enabled and the content is not already encrypted, encrypt it
    if audio_crypto.is_enabled() and not is_encrypted:
        try:
            content = audio_crypto.encrypt(content)
            logger.info(f"Plaintext audio file encrypted before saving on disk ({len(content)} bytes).")
        except Exception as e:
            logger.error(f"Failed to encrypt file on save: {e}")
            raise HTTPException(status_code=500, detail=f"Erreur de chiffrement: {e}")

    async with aiofiles.open(file_path, "wb") as f:
        await f.write(content)

    logger.info("Audio saved: %s (%d bytes)", sanitize_log(file_path), len(content))

    # ── Create DB record ────────────────────────────────────────
    record = AudioRecord(
        ticket_id=ticket_id,
        file_path=file_path,
        original_filename=file.filename,
        status="PROCESSING",
    )
    db.add(record)
    try:
        db.commit()
        db.refresh(record)
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=409,
            detail=f"Une transcription existe déjà pour le ticket {ticket_id}",
        )

    # ── Transcribe with Whisper ─────────────────────────────────
    temp_transcribe_path = file_path
    temp_path = None
    try:
        # Check if the file is encrypted and decrypt it to a temporary file for Whisper
        if audio_crypto.is_enabled():
            try:
                async with aiofiles.open(file_path, "rb") as rf:
                    encrypted_data = await rf.read()

                # Decrypt
                decrypted_data = audio_crypto.decrypt(encrypted_data)

                # Create a secure temporary file
                import tempfile
                ext = os.path.splitext(file_path)[1] or ".wav"
                temp_fd, temp_path = tempfile.mkstemp(suffix=ext, dir=ticket_dir)
                os.close(temp_fd)

                async with aiofiles.open(temp_path, "wb") as wf:
                    await wf.write(decrypted_data)

                temp_transcribe_path = temp_path
                logger.info("Temporary decrypted file created for Whisper: %s", sanitize_log(temp_transcribe_path))
            except Exception as dec_err:
                # Decryption failed (meaning it's already plaintext, e.g. an old or unencrypted file)
                logger.warning("File %s could not be decrypted. Treating as plaintext. Error: %s", sanitize_log(file_path), sanitize_log(dec_err))
                temp_transcribe_path = file_path

        result = whisper_model.transcribe(temp_transcribe_path, language="fr", fp16=False)

        text = result.get("text", "").strip()
        raw_segments = result.get("segments", [])

        # Build segments list
        segments = [
            {"start": round(s["start"], 2), "end": round(s["end"], 2), "text": s["text"].strip()}
            for s in raw_segments
        ]

        # Compute confidence from average log probability
        if raw_segments:
            avg_logprob = sum(s.get("avg_logprob", -0.5) for s in raw_segments) / len(raw_segments)
            confidence = round(min(1.0, max(0.0, 1.0 + avg_logprob)), 3)
        else:
            confidence = 0.0

        # Duration from last segment
        duration_seconds = round(raw_segments[-1]["end"], 2) if raw_segments else 0.0

        # Update DB record
        record.transcription = text
        record.confidence = confidence
        record.duration_seconds = duration_seconds
        record.segments = segments
        record.status = "DONE"
        db.commit()

        logger.info("Transcription done for ticket %s: %d chars, confidence=%s, duration=%ss",
                     sanitize_log(ticket_id), len(text), confidence, duration_seconds)

        return TranscribeResponse(
            ticket_id=ticket_id,
            text=text,
            confidence=confidence,
            duration_seconds=duration_seconds,
            segments=[{"start": s["start"], "end": s["end"], "text": s["text"]} for s in segments],
        )

    except Exception as e:
        logger.error("Whisper transcription failed for ticket %s: %s", sanitize_log(ticket_id), sanitize_log(e))
        record.status = "FAILED"
        record.error_message = str(e)
        db.commit()
        raise HTTPException(status_code=500, detail=f"Erreur de transcription: {e}")

    finally:
        # Securely remove temporary decrypted file if created
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
                logger.info(f"Temporary decrypted file securely deleted: {temp_path}")
            except OSError as cleanup_err:
                logger.warning(f"Failed to delete temporary decrypted file {temp_path}: {cleanup_err}")



@router.post("/quick")
async def quick_transcribe(
    request: Request,
    file: Annotated[UploadFile, File(...)],
):
    """
    Quick transcription for WhatsApp voice messages.
    No ticket_id required, no DB record — just returns the text.
    """
    whisper_model = request.app.state.whisper_model
    if whisper_model is None:
        raise HTTPException(
            status_code=503,
            detail="Le modèle Whisper n'est pas chargé.",
        )

    content = await file.read()
    if len(content) > MAX_SIZE_BYTES:
        raise HTTPException(status_code=400, detail="Fichier trop volumineux.")

    # Try to decrypt if encrypted
    if audio_crypto.is_enabled():
        try:
            content = audio_crypto.decrypt(content)
            logger.info("Quick transcription: decrypted incoming encrypted audio.")
        except Exception:
            # Ignore decryption error, treat as plaintext
            pass

    # Save to temp file
    tmp_path = os.path.join(settings.AUDIO_STORAGE_PATH, f"wa_{uuid.uuid4()}.ogg")
    os.makedirs(os.path.dirname(tmp_path), exist_ok=True)

    async with aiofiles.open(tmp_path, "wb") as f:
        await f.write(content)


    try:
        result = whisper_model.transcribe(tmp_path, language="fr", fp16=False)
        text = result.get("text", "").strip()
        duration = round(result.get("segments", [{}])[-1].get("end", 0), 2) if result.get("segments") else 0

        logger.info(f"Quick transcription: {len(text)} chars, {duration}s")
        return {"text": text, "duration_seconds": duration}
    except Exception as e:
        logger.error("Quick transcription failed: %s", sanitize_log(e))
        raise HTTPException(status_code=500, detail=f"Erreur: {e}")
    finally:
        # Cleanup temp file
        try:
            os.remove(tmp_path)
        except OSError:
            pass


@router.get("/{ticket_id}", response_model=AudioRecordResponse)
def get_transcription(ticket_id: str, db: Annotated[Session, Depends(get_db)]):
    """Retrieve an existing transcription by ticket ID."""
    record = db.query(AudioRecord).filter(AudioRecord.ticket_id == ticket_id).first()
    if not record:
        raise HTTPException(status_code=404, detail=f"Aucune transcription pour le ticket {ticket_id}")
    return record

