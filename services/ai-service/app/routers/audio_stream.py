"""
Audio streaming router — serves decrypted audio files to the frontend.

GET /api/audio/{ticket_id}/{filename}  → stream decrypted audio
GET /api/audio/{ticket_id}             → stream audio by ticket_id (auto-resolve filename)
"""
import logging
import mimetypes
import os
import re

from fastapi import APIRouter, HTTPException
from fastapi.responses import Response

from app.core.config import settings
from app.core.crypto import AudioCrypto
from app.core.logging_utils import sanitize_log

logger = logging.getLogger("ai-service.audio-stream")

audio_crypto = AudioCrypto(settings.AUDIO_ENCRYPTION_KEY)

router = APIRouter(prefix="/api/audio", tags=["audio-stream"])

# Map of extensions to MIME types for audio
AUDIO_MIME_TYPES = {
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
    ".ogg": "audio/ogg",
    ".webm": "audio/webm",
    ".flac": "audio/flac",
    ".m4a": "audio/mp4",
    ".aac": "audio/aac",
    ".mp4": "audio/mp4",
}


def _sanitize_path_segment(segment: str) -> str:
    """Sanitize a path segment to prevent path traversal."""
    if not re.match(r'^[a-zA-Z0-9_.\-]+$', segment):
        raise HTTPException(status_code=400, detail="Invalid path segment")
    return segment


def _read_and_decrypt(file_path: str) -> tuple[bytes, str]:
    """Read a file from disk and decrypt if encrypted. Returns (audio_bytes, mime_type)."""
    if not os.path.isfile(file_path):
        raise HTTPException(status_code=404, detail="Audio file not found")

    with open(file_path, "rb") as f:
        raw_bytes = f.read()

    # Determine MIME type from extension
    ext = os.path.splitext(file_path)[1].lower()
    mime_type = AUDIO_MIME_TYPES.get(ext, "application/octet-stream")

    # Try to decrypt if encryption is enabled
    if audio_crypto.is_enabled():
        try:
            decrypted = audio_crypto.decrypt(raw_bytes)
            logger.debug("Audio file decrypted for streaming: %s", sanitize_log(file_path))
            return decrypted, mime_type
        except Exception:
            # Decryption failed — file is likely plaintext (backward compat)
            logger.debug("File %s is not encrypted, serving as-is", sanitize_log(file_path))
            return raw_bytes, mime_type

    return raw_bytes, mime_type


@router.get(
    "/{ticket_id}/{filename}",
    responses={
        400: {"description": "Invalid path segment"},
        404: {"description": "Audio file not found"}
    }
)
def stream_audio_by_filename(ticket_id: str, filename: str):
    """
    Stream a specific audio file for a ticket.
    Automatically decrypts if the file is encrypted (GDPR compliance).
    """
    ticket_id = _sanitize_path_segment(ticket_id)
    filename = _sanitize_path_segment(filename)

    file_path = os.path.join(settings.AUDIO_STORAGE_PATH, ticket_id, filename)
    audio_bytes, mime_type = _read_and_decrypt(file_path)

    return Response(
        content=audio_bytes,
        media_type=mime_type,
        headers={
            "Content-Disposition": f'inline; filename="{filename}"',
            "Cache-Control": "private, max-age=3600",
        },
    )


@router.get(
    "/{ticket_id}",
    responses={
        400: {"description": "Invalid path segment"},
        404: {"description": "Audio directory or file not found"}
    }
)
def stream_audio_by_ticket(ticket_id: str):
    """
    Stream the audio file for a ticket (auto-resolves the filename).
    Looks in the ticket's audio directory and returns the first audio file found.
    """
    ticket_id = _sanitize_path_segment(ticket_id)

    ticket_dir = os.path.join(settings.AUDIO_STORAGE_PATH, ticket_id)
    if not os.path.isdir(ticket_dir):
        raise HTTPException(status_code=404, detail=f"No audio directory for ticket {ticket_id}")

    # Find the first audio file in the directory
    audio_files = [
        f for f in os.listdir(ticket_dir)
        if os.path.splitext(f)[1].lower() in AUDIO_MIME_TYPES
    ]

    if not audio_files:
        raise HTTPException(status_code=404, detail=f"No audio file found for ticket {ticket_id}")

    filename = audio_files[0]  # Take the first (and usually only) audio file
    file_path = os.path.join(ticket_dir, filename)
    audio_bytes, mime_type = _read_and_decrypt(file_path)

    return Response(
        content=audio_bytes,
        media_type=mime_type,
        headers={
            "Content-Disposition": f'inline; filename="{filename}"',
            "Cache-Control": "private, max-age=3600",
        },
    )
