"""
Tests for NLP analysis router — 8 test cases.
"""
import pytest

from app.routers.analysis import (
    analyze_sentiment,
    classify_sinistre,
    extract_keywords_spacy,
    extract_piece_reference,
)
from tests.conftest import MockNLP


# ── Unit tests for NLP functions ─────────────────────────────────


class TestExtractPieceReference:
    """Tests for piece reference extraction."""

    def test_extract_piece_reference_regex(self):
        """Regex pattern [A-Z]{2,4}-[0-9]{4,8} should match."""
        text = "La pièce AB-12345 est défectueuse"
        result = extract_piece_reference(text)
        assert result == "AB-12345"

    def test_extract_piece_reference_dot_pattern(self):
        """Regex pattern [A-Z]{2,4}.[0-9]{4,8} should also match."""
        text = "Référence ALT.4892 pour l'alternateur"
        result = extract_piece_reference(text)
        assert result == "ALT.4892"

    def test_extract_piece_reference_keyword(self):
        """Known part keywords should be matched."""
        text = "le moteur ne fonctionne plus depuis hier"
        result = extract_piece_reference(text)
        assert result == "moteur"

    def test_extract_piece_reference_none(self):
        """No match should return None."""
        text = "bonjour je voudrais des informations"
        result = extract_piece_reference(text)
        assert result is None


class TestClassifySinistre:
    """Tests for sinistre type classification."""

    def test_classify_sinistre_panne(self):
        text = "ma voiture ne demarre plus ce matin"
        assert classify_sinistre(text) == "PANNE"

    def test_classify_sinistre_accident(self):
        text = "après le choc la portière ne s'ouvre plus"
        assert classify_sinistre(text) == "ACCIDENT"

    def test_classify_sinistre_usure(self):
        text = "les plaquettes sont complètement usées et deteriorees"
        assert classify_sinistre(text) == "USURE"

    def test_classify_sinistre_defaut(self):
        text = "le véhicule est neuf, c'est un defaut d'usine"
        assert classify_sinistre(text) == "DEFAUT_FABRICATION"

    def test_classify_sinistre_vol(self):
        text = "ma voiture a été volée cette nuit"
        assert classify_sinistre(text) == "VOL"

    def test_classify_sinistre_unknown(self):
        text = "bonjour je voudrais un rendez-vous"
        assert classify_sinistre(text) == "INCONNU"


class TestAnalyzeSentiment:
    """Tests for sentiment analysis."""

    def test_sentiment_positif(self):
        text = "le service était excellent et rapide, je suis satisfait"
        assert analyze_sentiment(text) == "POSITIF"

    def test_sentiment_negatif(self):
        text = "c'est horrible, service lent et defectueux"
        assert analyze_sentiment(text) == "NEGATIF"

    def test_sentiment_neutre(self):
        text = "je viens pour une demande de renseignement"
        assert analyze_sentiment(text) == "NEUTRE"


class TestExtractKeywords:
    """Tests for keyword extraction with spaCy."""

    def test_extract_keywords_with_spacy(self):
        nlp = MockNLP()
        keywords = extract_keywords_spacy("Mon alternateur Renault est en panne", nlp)
        assert isinstance(keywords, list)
        assert len(keywords) <= 10
        assert "renault" in keywords  # from MockEntity ORG

    def test_extract_keywords_without_spacy(self):
        keywords = extract_keywords_spacy("Mon alternateur est defectueux", None)
        assert isinstance(keywords, list)
        assert len(keywords) <= 10


class TestAnalyzeEndpoint:
    """Integration test for POST /analyze endpoint."""

    def test_analyze_endpoint_full(self, client):
        """POST /analyze with valid text should return full AnalysisResponse."""
        response = client.post("/analyze/", json={
            "ticket_id": "test-ticket-001",
            "text": "Bonjour, mon alternateur AB-4892 est en panne depuis hier. "
                    "Le service était mauvais et lent.",
        })
        assert response.status_code == 200
        data = response.json()
        assert data["ticket_id"] == "test-ticket-001"
        assert data["piece_reference"] == "AB-4892"
        assert data["type_sinistre"] == "PANNE"
        assert data["sentiment"] == "NEGATIF"
        assert isinstance(data["keywords"], list)
        assert data["confidence"] == 0.9  # piece found + sinistre found

    def test_analyze_empty_text(self, client):
        """POST /analyze with empty text should return 400."""
        response = client.post("/analyze/", json={
            "ticket_id": "test-ticket-002",
            "text": "",
        })
        assert response.status_code == 400

    def test_get_analysis_not_found(self, client):
        """GET /analyze/fake-id should return 404."""
        response = client.get("/analyze/fake-ticket-id")
        assert response.status_code == 404

    def test_get_analysis_after_create(self, client):
        """After creating an analysis, GET should return it."""
        # Create first
        client.post("/analyze/", json={
            "ticket_id": "test-ticket-003",
            "text": "Mon moteur est en panne depuis hier",
        })
        # Then retrieve
        response = client.get("/analyze/test-ticket-003")
        assert response.status_code == 200
        assert response.json()["ticket_id"] == "test-ticket-003"
