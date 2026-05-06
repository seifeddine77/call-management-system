"""
Analysis router — Local NLP (spaCy + regex).

POST /analyze       → analyse text → return AnalysisResponse
GET  /analyze/{tid} → retrieve existing analysis
"""
import logging
import re
from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models import AnalysisResult
from app.schemas import AnalysisRequest, AnalysisResponse, AnalysisResultResponse

logger = logging.getLogger("ai-service.analysis")

router = APIRouter(prefix="/analyze", tags=["analysis"])

# ── Reference data ──────────────────────────────────────────────

PIECE_KEYWORDS = [
    "moteur", "boite", "embrayage", "alternateur", "demarreur", "carburateur",
    "radiateur", "compresseur", "pompe", "injecteur", "calculateur",
    "amortisseur", "freins", "plaquettes", "turbo", "courroie", "joint",
    "soupape", "bielle", "vilebrequin", "culasse",
]

SINISTRE_KEYWORDS = {
    "PANNE": ["panne", "ne demarre plus", "ne démarre plus", "arrete", "arrêté",
              "bloque", "bloqué", "ne fonctionne", "tombe en panne", "en panne",
              "défectueux", "defectueux", "ne marche plus", "cassé", "casse"],
    "ACCIDENT": ["accident", "choc", "collision", "impact", "accrochage", "carambolage",
                 "endommagé", "endommage"],
    "USURE": ["usure", "usé", "usée", "usées", "deteriore", "détérioré", "abimé",
              "abime", "dégradé", "degrade", "vieillissement"],
    "DEFAUT_FABRICATION": ["defaut de fabrication", "défaut de fabrication",
                           "depuis achat", "sortie usine", "d'usine", "vice caché"],
    "VOL": ["volé", "vole", "vol ", "disparition", "disparu", "manquant", "dérobé", "dérobe"],
}

# ── Sentiment words (whole-word matching via regex) ─────────────
# Words that indicate NEGATIVE sentiment — includes French negation prefixes
NEGATIVE_EXPRESSIONS = [
    "mécontent", "mecontent", "insatisfait", "furieux", "en colère", "en colere",
    "colère", "colere", "horrible", "inadmissible", "inacceptable", "nul",
    "mauvais", "pire", "problème", "probleme", "défectueux", "defectueux",
    "lent", "catastrophe", "déplorable", "deplorable", "scandaleux",
    "pas content", "pas satisfait", "pas acceptable", "jamais vu",
    "arnaque", "honte", "ridicule", "incompétent", "incompetent",
]

POSITIVE_EXPRESSIONS = [
    "satisfait", "excellent", "efficace", "parfait", "super", "génial", "genial",
    "rapide", "merci", "bravo", "formidable", "impeccable", "professionnel",
    "content", "très bien", "tres bien", "bon travail", "bonne qualité",
    "bien fait", "agréable", "agreable", "recommande",
]

# Regex patterns for piece references
PIECE_REF_PATTERNS = [
    re.compile(r"[A-Z]{2,4}-\d{4,8}"),       # e.g. ALT-4892, AB-12345
    re.compile(r"[A-Z]{2,4}\.\d{4,8}"),       # e.g. ALT.4892
]


# ── NLP helper functions ────────────────────────────────────────

def extract_piece_reference(text: str) -> Optional[str]:
    """Extract piece reference from text using regex + keyword matching."""
    # Try regex patterns first (on original text for uppercase)
    for pattern in PIECE_REF_PATTERNS:
        match = pattern.search(text)
        if match:
            return match.group()

    # Then try keyword matching
    text_lower = text.lower()
    for keyword in PIECE_KEYWORDS:
        if keyword in text_lower:
            return keyword

    return None


def classify_sinistre(text: str) -> str:
    """Classify the type of incident from text using keyword matching."""
    text_lower = text.lower()
    for sinistre_type, keywords in SINISTRE_KEYWORDS.items():
        for keyword in keywords:
            if keyword in text_lower:
                return sinistre_type
    return "INCONNU"


def _count_expressions(text_lower: str, expressions: List[str]) -> int:
    """Count how many expressions from the list appear in text (whole-phrase match)."""
    count = 0
    for expr in expressions:
        # Use word boundary regex for single words, plain 'in' for multi-word phrases
        if " " in expr:
            if expr in text_lower:
                count += 1
        else:
            # Word boundary match to avoid 'bon' matching 'bonjour'
            if re.search(r'\b' + re.escape(expr) + r'\b', text_lower):
                count += 1
    return count


def analyze_sentiment(text: str) -> str:
    """Sentiment analysis using whole-word/expression matching with negation awareness."""
    text_lower = text.lower()

    neg_count = _count_expressions(text_lower, NEGATIVE_EXPRESSIONS)
    pos_count = _count_expressions(text_lower, POSITIVE_EXPRESSIONS)

    if neg_count > pos_count:
        return "NEGATIF"
    elif pos_count > neg_count:
        return "POSITIF"
    return "NEUTRE"


def extract_keywords_spacy(text: str, nlp) -> List[str]:
    """Extract keywords using spaCy NER + NOUN extraction."""
    if nlp is None:
        # Fallback: simple word extraction without spaCy
        words = set()
        for word in text.split():
            cleaned = re.sub(r"[^\w]", "", word.lower())
            if len(cleaned) > 3:
                words.add(cleaned)
        return sorted(words)[:10]

    doc = nlp(text)
    keywords = set()

    # Named entities (LOC, ORG, MISC, PER)
    for ent in doc.ents:
        if ent.label_ in ("LOC", "ORG", "MISC", "PER"):
            keywords.add(ent.text.lower())

    # NOUN tokens (not stopwords, len > 3)
    for token in doc:
        if token.pos_ == "NOUN" and not token.is_stop and len(token.text) > 3:
            keywords.add(token.lemma_.lower())

    # Deduplicate and return top 10
    return sorted(keywords)[:10]


# ── Endpoint ────────────────────────────────────────────────────

@router.post("/", response_model=AnalysisResponse)
def analyze_text(
    request_body: AnalysisRequest,
    request: Request,
    db: Annotated[Session, Depends(get_db)],
):
    """
    Analyze text using local NLP (no external API calls).
    Extracts: piece reference, sinistre type, sentiment, keywords.
    """
    ticket_id = request_body.ticket_id
    text = request_body.text

    if not text or not text.strip():
        raise HTTPException(status_code=400, detail="Le texte à analyser est vide")

    # 1. Piece reference extraction
    piece_reference = extract_piece_reference(text)

    # 2. Sinistre type classification
    type_sinistre = classify_sinistre(text)

    # 3. Sentiment analysis
    sentiment = analyze_sentiment(text)

    # 4. Keywords extraction (spaCy)
    nlp = request.app.state.nlp
    keywords = extract_keywords_spacy(text, nlp)

    # 5. Confidence score
    confidence = 0.9 if (piece_reference and type_sinistre != "INCONNU") else 0.6

    logger.info(
        f"Analysis for ticket {ticket_id}: "
        f"piece={piece_reference}, sinistre={type_sinistre}, "
        f"sentiment={sentiment}, keywords={len(keywords)}, "
        f"confidence={confidence}"
    )

    # Save to DB
    result = AnalysisResult(
        ticket_id=ticket_id,
        piece_reference=piece_reference,
        type_sinistre=type_sinistre,
        sentiment=sentiment,
        keywords=keywords,
        confidence=confidence,
        raw_text=text,
    )
    db.add(result)
    db.commit()
    db.refresh(result)

    return AnalysisResponse(
        ticket_id=ticket_id,
        piece_reference=piece_reference,
        type_sinistre=type_sinistre,
        sentiment=sentiment,
        keywords=keywords,
        confidence=confidence,
    )


@router.get("/{ticket_id}", response_model=AnalysisResultResponse)
def get_analysis(ticket_id: str, db: Annotated[Session, Depends(get_db)]):
    """Retrieve an existing analysis by ticket ID."""
    result = db.query(AnalysisResult).filter(AnalysisResult.ticket_id == ticket_id).first()
    if not result:
        raise HTTPException(status_code=404, detail=f"Aucune analyse pour le ticket {ticket_id}")
    return result
