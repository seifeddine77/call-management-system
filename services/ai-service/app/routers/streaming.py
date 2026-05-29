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

logger = logging.getLogger("ai-service.streaming")

router = APIRouter(tags=["streaming"])

# Minimum buffer size before triggering transcription (~3 seconds of audio)
MIN_BUFFER_BYTES = 48_000
# Maximum buffer size (~30 seconds) to avoid memory issues
MAX_BUFFER_BYTES = 960_000


@router.websocket("/ws/transcribe/{ticket_id}")
async def stream_transcribe(websocket: WebSocket, ticket_id: str):
    """
    WebSocket endpoint for real-time audio transcription.

    The client (browser MediaRecorder) sends binary audio chunks.
    The server accumulates them and periodically transcribes with Whisper.
    """
    await websocket.accept()
    logger.info("🎙️ [STREAM] WebSocket connected for ticket %s", ticket_id)

    whisper_model = websocket.app.state.whisper_model
    if whisper_model is None:
        await websocket.send_json({
            "type": "error",
            "detail": "Whisper model not loaded. Streaming transcription unavailable."
        })
        await websocket.close(code=1011)
        return

    audio_buffer = bytearray()
    full_transcription_parts: list[str] = []
    session_start = time.time()
    chunk_count = 0

    try:
        while True:
            # Receive data (binary audio or text command)
            message = await websocket.receive()

            if message.get("type") == "websocket.disconnect":
                break

            # Text message = control command
            if "text" in message:
                text_data = message["text"]
                if text_data.strip().upper() == "STOP":
                    logger.info("🛑 [STREAM] STOP received for ticket %s", ticket_id)
                    break
                continue

            # Binary message = audio chunk
            if "bytes" in message:
                audio_chunk = message["bytes"]
                audio_buffer.extend(audio_chunk)
                chunk_count += 1

                # Transcribe when buffer is large enough
                if len(audio_buffer) >= MIN_BUFFER_BYTES:
                    # Cap buffer size
                    if len(audio_buffer) > MAX_BUFFER_BYTES:
                        audio_buffer = bytearray(audio_buffer[-MAX_BUFFER_BYTES:])

                    partial_text = _transcribe_buffer(
                        whisper_model, bytes(audio_buffer), ticket_id
                    )

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

                    # Reset buffer for next segment
                    audio_buffer = bytearray()

    except WebSocketDisconnect:
        logger.info("🔌 [STREAM] Client disconnected for ticket %s", ticket_id)
    except Exception as e:
        logger.error("❌ [STREAM] Error for ticket %s: %s", ticket_id, e)
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
        ticket_id, len(full_text), duration, chunk_count
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

    # Save to database if we have content
    if full_text:
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
                logger.info(f"💾 [STREAM] Transcription saved for ticket {ticket_id}")
            finally:
                db.close()
        except Exception as e:
            logger.error(f"❌ [STREAM] DB save failed for ticket {ticket_id}: {e}")

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
            logger.debug(f"🗣️ [STREAM] Partial for {ticket_id}: {text[:80]}...")

        return text

    except Exception as e:
        logger.warning(f"⚠️ [STREAM] Transcription chunk failed for {ticket_id}: {e}")
        return ""

    finally:
        # Clean up temp file
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
