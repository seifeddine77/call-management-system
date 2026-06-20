"""
RGPD — Effacement des données personnelles d'un ticket côté ai-service.

Appelé par le ticket-service lors de l'anonymisation d'un ticket (droit à l'oubli,
art. 17 RGPD) pour propager l'effacement aux données détenues par l'ai-service :
    - fichiers audio chiffrés sur disque (/var/audio/<ticket_id>),
    - enregistrements audio en base (audio_records),
    - analyses NLP en base (analysis_results, contenant la transcription).

Endpoint idempotent : ré-appeler sur un ticket déjà purgé renvoie des compteurs à 0.
"""
import logging
import os
import shutil
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.database import get_db
from app.core.logging_utils import sanitize_log
from app.models import AnalysisResult, AudioRecord

logger = logging.getLogger("ai-service.rgpd")
router = APIRouter(prefix="/api/ai/rgpd", tags=["rgpd"])


@router.delete("/tickets/{ticket_id}")
def erase_ticket_data(ticket_id: str, db: Annotated[Session, Depends(get_db)]):
    """Supprime audio (fichiers + base) et analyses NLP d'un ticket (RGPD — droit à l'oubli)."""
    audio_deleted = (
        db.query(AudioRecord)
        .filter(AudioRecord.ticket_id == ticket_id)
        .delete(synchronize_session=False)
    )
    analysis_deleted = (
        db.query(AnalysisResult)
        .filter(AnalysisResult.ticket_id == ticket_id)
        .delete(synchronize_session=False)
    )
    db.commit()

    # Suppression physique des fichiers audio chiffrés sur disque.
    files_removed = False
    ticket_dir = os.path.join(settings.AUDIO_STORAGE_PATH, ticket_id)
    if os.path.isdir(ticket_dir):
        shutil.rmtree(ticket_dir, ignore_errors=True)
        files_removed = not os.path.isdir(ticket_dir)

    logger.info(
        "RGPD: données IA effacées pour ticket %s (audio_records=%d, analyses=%d, fichiers=%s)",
        sanitize_log(ticket_id), audio_deleted, analysis_deleted, files_removed,
    )
    return {
        "ticketId": ticket_id,
        "audioRecordsDeleted": audio_deleted,
        "analysisResultsDeleted": analysis_deleted,
        "audioFilesRemoved": files_removed,
    }
