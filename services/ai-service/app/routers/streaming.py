"""
Real-time streaming transcription via WebSocket.

Client sends binary audio chunks (WebM/opus from MediaRecorder).
Server accumulates a buffer, transcribes with Whisper every ~5s,
and sends back partial/final transcription results as JSON.

Protocol:
  → Client sends: binary audio chunks (bytes)
  → Client sends: text "STOP" to end session
  ← Server sends: {"type":"partial","text":"...","elapsed":3.2}
  ← Server sends: {"type":"final","text":"...","duration":45.1}
  ← Server sends: {"type":"error","detail":"..."}
"""
import io
import json
import logging
import os
import tempfile
import time
import uuid

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from starlette.websockets import WebSocketState
from app.core.logging_utils import sanitize_log

logger = logging.getLogger("ai-service.streaming")

router = APIRouter(tags=["streaming"])

# Minimum buffer size before triggering transcription (~3 seconds of audio)
MIN_BUFFER_BYTES = 48_000
# Maximum buffer size (~30 seconds) to avoid memory issues
MAX_BUFFER_BYTES = 960_000


async def _transcribe_and_send(
    websocket: WebSocket,
    whisper_model,
    audio_buffer: bytearray,
    ticket_id: str,
    session_start: float,
    chunk_count: int,
    full_transcription_parts: list[str],
) -> bytearray:
    """Transcribe the current audio buffer and send a partial result to the client."""
    capped = bytearray(audio_buffer[-MAX_BUFFER_BYTES:]) if len(audio_buffer) > MAX_BUFFER_BYTES else audio_buffer
    partial_text = _transcribe_buffer(whisper_model, bytes(capped), ticket_id)
    if partial_text:
        elapsed = round(time.time() - session_start, 1)
        if websocket.client_state == WebSocketState.CONNECTED:
            await websocket.send_json({
                "type": "partial",
                "text": partial_text,
                "elapsed": elapsed,
                "chunks": chunk_count,
            })
        full_transcription_parts.append(partial_text)
    # Reset buffer
    return bytearray()


async def _read_audio_chunks(websocket: WebSocket, whisper_model, ticket_id: str, session_start: float) -> tuple[list[str], int, bytearray]:
    audio_buffer = bytearray()
    full_transcription_parts: list[str] = []
    chunk_count = 0

    while True:
        message = await websocket.receive()

        if message.get("type") == "websocket.disconnect":
            break

        # Text message = control command
        if "text" in message:
            text_data = message["text"]
            if text_data.strip().upper() == "STOP":
                logger.info("🛑 [STREAM] STOP received for ticket %s", sanitize_log(ticket_id))
                break
            continue

        # Binary message = audio chunk
        if "bytes" in message:
            audio_buffer.extend(message["bytes"])
            chunk_count += 1

            if len(audio_buffer) >= MIN_BUFFER_BYTES:
                audio_buffer = await _transcribe_and_send(
                    websocket, whisper_model, audio_buffer,
                    ticket_id, session_start, chunk_count, full_transcription_parts
                )

    return full_transcription_parts, chunk_count, audio_buffer


def _save_transcription_to_db(ticket_id: str, full_text: str, duration: float):
    if not full_text:
        return
    try:
        from app.core.database import SessionLocal
        from app.models import AudioRecord

        db = SessionLocal()
        try:
            record = db.query(AudioRecord).filter(
                AudioRecord.ticket_id == ticket_id
            ).first()

            if record:
                # Append to existing transcription
                existing = record.transcription or ""
                record.transcription = (existing + " " + full_text).strip()
                record.status = "DONE"
            else:
                record = AudioRecord(
                    ticket_id=ticket_id,
                    transcription=full_text,
                    duration_seconds=duration,
                    status="DONE",
                    file_path=f"streaming/{ticket_id}",
                    original_filename="live-stream",
                )
                db.add(record)

            db.commit()
            logger.info("💾 [STREAM] Transcription saved for ticket %s", sanitize_log(ticket_id))
        finally:
            db.close()
    except Exception:
        logger.exception("❌ [STREAM] DB save failed for ticket %s", sanitize_log(ticket_id))


@router.websocket("/ws/transcribe/{ticket_id}")
async def stream_transcribe(websocket: WebSocket, ticket_id: str):
    """
    WebSocket endpoint for real-time audio transcription.

    The client (browser MediaRecorder) sends binary audio chunks.
    The server accumulates them and periodically transcribes with Whisper.
    """
    await websocket.accept()
    logger.info("🎙️ [STREAM] WebSocket connected for ticket %s", sanitize_log(ticket_id))

    whisper_model = websocket.app.state.whisper_model
    if whisper_model is None:
        await websocket.send_json({
            "type": "error",
            "detail": "Whisper model not loaded. Streaming transcription unavailable."
        })
        await websocket.close(code=1011)
        return

    session_start = time.time()
    full_transcription_parts: list[str] = []
    chunk_count = 0
    audio_buffer = bytearray()

    try:
        full_transcription_parts, chunk_count, audio_buffer = await _read_audio_chunks(
            websocket, whisper_model, ticket_id, session_start
        )
    except WebSocketDisconnect:
        logger.info("🔌 [STREAM] Client disconnected for ticket %s", sanitize_log(ticket_id))
    except Exception as e:
        logger.exception("❌ [STREAM] Error for ticket %s", sanitize_log(ticket_id))
        try:
            if websocket.client_state == WebSocketState.CONNECTED:
                await websocket.send_json({"type": "error", "detail": str(e)})
        except Exception as send_err:  # noqa: BLE001
            logger.debug("[STREAM] Could not send error to client: %s", send_err)

    # ── Final transcription ──────────────────────────────────────
    # Transcribe any remaining buffer
    if len(audio_buffer) > 1000:
        final_chunk = _transcribe_buffer(whisper_model, bytes(audio_buffer), ticket_id)
        if final_chunk:
            full_transcription_parts.append(final_chunk)

    full_text = " ".join(full_transcription_parts).strip()
    duration = round(time.time() - session_start, 1)

    logger.info(
        "✅ [STREAM] Session complete for ticket %s: %d chars, %ss, %d chunks",
        sanitize_log(ticket_id), len(full_text), duration, chunk_count
    )

    # Send final result
    try:
        if websocket.client_state == WebSocketState.CONNECTED:
            await websocket.send_json({
                "type": "final",
                "text": full_text,
                "duration": duration,
                "chunks": chunk_count,
            })
    except Exception as send_err:  # noqa: BLE001
        logger.debug("[STREAM] Could not send final result: %s", send_err)

    _save_transcription_to_db(ticket_id, full_text, duration)

    # Clean close
    try:
        if websocket.client_state == WebSocketState.CONNECTED:
            await websocket.close()
    except Exception as close_err:  # noqa: BLE001
        logger.debug("[STREAM] WebSocket already closed: %s", close_err)



def _transcribe_buffer(whisper_model, audio_bytes: bytes, ticket_id: str) -> str:
    """
    Write audio buffer to a temp file and transcribe with Whisper.
    Returns the transcribed text or empty string on failure.
    """
    tmp_path = None
    try:
        # Write buffer to temp file (Whisper needs a file path)
        with tempfile.NamedTemporaryFile(
            suffix=".webm", delete=False, prefix=f"stream_{ticket_id}_"
        ) as tmp:
            tmp.write(audio_bytes)
            tmp_path = tmp.name

        # Transcribe
        result = whisper_model.transcribe(tmp_path, language="fr", fp16=False)
        text = result.get("text", "").strip()

        if text:
            logger.debug("🗣️ [STREAM] Partial for %s: %s...", sanitize_log(ticket_id), sanitize_log(text[:80]))

        return text

    except Exception as e:
        logger.warning("⚠️ [STREAM] Transcription chunk failed for %s: %s", sanitize_log(ticket_id), sanitize_log(e))
        return ""

    finally:
        # Clean up temp file
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
