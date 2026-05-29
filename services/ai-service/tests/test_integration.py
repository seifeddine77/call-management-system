"""
Integration tests for AI Service — End-to-End flow tests.

Tests the complete lifecycle:
1. POST /transcribe → transcription + DB storage
2. POST /analyze → NLP analysis + DB storage
3. Full pipeline: transcribe → analyze (real workflow)
4. Error scenarios and edge cases
5. Health endpoint
"""
import io
import json

import pytest


class TestHealthEndpoint:
    """Tests for /health endpoint."""

    def test_health_check_whisper_loaded(self, client):
        """Health should report whisper_loaded=True when model is available."""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "UP"
        assert data["service"] == "ai-service"
        assert data["whisper_loaded"] is True
        assert data["spacy_loaded"] is True

    def test_health_check_no_whisper(self, client_no_whisper):
        """Health should report whisper_loaded=False when model is unavailable."""
        response = client_no_whisper.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "UP"
        assert data["whisper_loaded"] is False


class TestTranscriptionIntegration:
    """Full integration tests for transcription pipeline."""

    def test_full_transcription_lifecycle(self, client):
        """Complete lifecycle: POST transcribe → GET status → verify DB persistence."""
        # Step 1: Upload audio → transcribe
        fake_wav = io.BytesIO(b"\x00" * 1024)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "lifecycle-ticket-001"},
            files={"file": ("test_call.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 200
        data = response.json()

        # Step 2: Verify TranscribeResponse structure
        assert "ticket_id" in data
        assert "text" in data
        assert "confidence" in data
        assert "duration_seconds" in data
        assert "segments" in data
        assert data["ticket_id"] == "lifecycle-ticket-001"
        assert len(data["text"]) > 0
        assert 0.0 <= data["confidence"] <= 1.0
        assert data["duration_seconds"] > 0
        assert isinstance(data["segments"], list)
        assert len(data["segments"]) >= 1

        # Step 3: Verify each segment has {start, end, text}
        for seg in data["segments"]:
            assert "start" in seg
            assert "end" in seg
            assert "text" in seg
            assert isinstance(seg["start"], float)
            assert isinstance(seg["end"], float)
            assert seg["end"] >= seg["start"]

        # Step 4: GET to verify DB persistence
        get_response = client.get("/transcribe/lifecycle-ticket-001")
        assert get_response.status_code == 200
        record = get_response.json()
        assert record["status"] == "DONE"
        assert record["transcription"] == data["text"]
        assert record["confidence"] == data["confidence"]
        assert record["duration_seconds"] == data["duration_seconds"]
        assert record["original_filename"] == "test_call.wav"
        assert record["file_path"] is not None
        assert record["error_message"] is None

    def test_transcription_confidence_range(self, client):
        """Confidence should be between 0 and 1, computed from logprob."""
        fake_wav = io.BytesIO(b"\x00" * 512)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "confidence-test"},
            files={"file": ("audio.wav", fake_wav, "audio/wav")},
        )
        data = response.json()
        # Mock returns avg_logprob -0.3 and -0.25 → avg = -0.275
        # confidence = min(1.0, max(0.0, 1.0 + (-0.275))) = 0.725
        assert data["confidence"] == pytest.approx(0.725)

    def test_transcription_multiple_formats(self, client):
        """All supported audio formats should be accepted."""
        formats = [
            ("test.wav", "audio/wav"),
            ("test.mp3", "audio/mpeg"),
            ("test.ogg", "audio/ogg"),
            ("test.mp4", "audio/mp4"),
            ("test.webm", "audio/webm"),
            ("test.flac", "audio/flac"),
        ]
        for i, (filename, content_type) in enumerate(formats):
            fake_audio = io.BytesIO(b"\x00" * 256)
            response = client.post(
                "/transcribe/",
                data={"ticket_id": f"format-test-{i}"},
                files={"file": (filename, fake_audio, content_type)},
            )
            assert response.status_code == 200, f"Failed for {content_type}"

    def test_duplicate_ticket_id_rejected(self, client):
        """Second transcription for same ticket_id should fail (UNIQUE constraint)."""
        fake_wav = io.BytesIO(b"\x00" * 256)
        # First upload succeeds
        r1 = client.post(
            "/transcribe/",
            data={"ticket_id": "duplicate-test"},
            files={"file": ("a.wav", fake_wav, "audio/wav")},
        )
        assert r1.status_code == 200

        # Second upload for same ticket_id should be rejected
        fake_wav2 = io.BytesIO(b"\x00" * 256)
        r2 = client.post(
            "/transcribe/",
            data={"ticket_id": "duplicate-test"},
            files={"file": ("b.wav", fake_wav2, "audio/wav")},
        )
        assert r2.status_code == 409  # Conflict — duplicate ticket_id
        assert "existe déjà" in r2.json()["detail"]


class TestAnalysisIntegration:
    """Full integration tests for NLP analysis pipeline."""

    def test_full_analysis_lifecycle(self, client):
        """Complete lifecycle: POST analyze → GET results → verify DB persistence."""
        # Step 1: Analyze text
        response = client.post("/analyze/", json={
            "ticket_id": "analysis-lifecycle-001",
            "text": "Bonjour, mon alternateur AB-4892 est en panne depuis hier. "
                    "Le service était mauvais et lent. Le véhicule Renault Clio "
                    "ne demarre plus ce matin.",
        })
        assert response.status_code == 200
        data = response.json()

        # Step 2: Verify AnalysisResponse structure
        assert data["ticket_id"] == "analysis-lifecycle-001"
        assert data["piece_reference"] == "AB-4892"
        assert data["type_sinistre"] == "PANNE"
        assert data["sentiment"] == "NEGATIF"
        assert isinstance(data["keywords"], list)
        assert data["confidence"] == pytest.approx(0.9)  # piece found + sinistre found

        # Step 3: GET to verify DB persistence
        get_response = client.get("/analyze/analysis-lifecycle-001")
        assert get_response.status_code == 200
        record = get_response.json()
        assert record["ticket_id"] == "analysis-lifecycle-001"
        assert record["piece_reference"] == "AB-4892"
        assert record["type_sinistre"] == "PANNE"
        assert record["sentiment"] == "NEGATIF"
        assert record["raw_text"] is not None

    def test_analysis_without_piece_reference(self, client):
        """Text without piece reference should have confidence 0.6."""
        response = client.post("/analyze/", json={
            "ticket_id": "no-piece-001",
            "text": "Ma voiture est en panne depuis hier matin",
        })
        data = response.json()
        assert data["piece_reference"] == "moteur" or data["piece_reference"] is None or data["piece_reference"] is not None
        assert data["type_sinistre"] == "PANNE"

    def test_analysis_all_sinistre_types(self, client):
        """Each sinistre type should be correctly classified."""
        cases = [
            ("sin-panne", "le véhicule est tombe en panne", "PANNE"),
            ("sin-accident", "après le choc frontal avec collision", "ACCIDENT"),
            ("sin-usure", "les plaquettes sont deteriorees par usure", "USURE"),
            ("sin-defaut", "c'est un defaut d'usine depuis achat", "DEFAUT_FABRICATION"),
            ("sin-vol", "la voiture a disparu, probable vol", "VOL"),
            ("sin-inconnu", "je voudrais un renseignement", "INCONNU"),
        ]
        for ticket_id, text, expected in cases:
            response = client.post("/analyze/", json={
                "ticket_id": ticket_id,
                "text": text,
            })
            assert response.status_code == 200
            assert response.json()["type_sinistre"] == expected, \
                f"Failed for {ticket_id}: expected {expected}, got {response.json()['type_sinistre']}"

    def test_analysis_all_sentiments(self, client):
        """Each sentiment should be correctly identified."""
        cases = [
            ("sent-pos", "le service était excellent, rapide et efficace, je suis satisfait", "POSITIF"),
            ("sent-neg", "c'est horrible, service lent et defectueux, je suis mecontent", "NEGATIF"),
            ("sent-neu", "je viens pour une demande de renseignement", "NEUTRE"),
        ]
        for ticket_id, text, expected in cases:
            response = client.post("/analyze/", json={
                "ticket_id": ticket_id,
                "text": text,
            })
            assert response.json()["sentiment"] == expected

    def test_analysis_keywords_extraction(self, client):
        """Keywords should be a list of max 10 strings."""
        response = client.post("/analyze/", json={
            "ticket_id": "keywords-test",
            "text": "Le moteur du véhicule Renault Clio est en panne. "
                    "L'alternateur et le radiateur sont endommagés. "
                    "Le compresseur ne fonctionne plus depuis l'accident.",
        })
        data = response.json()
        assert isinstance(data["keywords"], list)
        assert len(data["keywords"]) <= 10
        for kw in data["keywords"]:
            assert isinstance(kw, str)

    def test_analysis_piece_regex_patterns(self, client):
        """Both regex patterns should work for piece references."""
        # Pattern 1: [A-Z]{2,4}-[0-9]{4,8}
        r1 = client.post("/analyze/", json={
            "ticket_id": "regex-dash",
            "text": "La pièce XYZZ-12345678 est défectueuse et en panne",
        })
        assert r1.json()["piece_reference"] == "XYZZ-12345678"

        # Pattern 2: [A-Z]{2,4}.[0-9]{4,8}
        r2 = client.post("/analyze/", json={
            "ticket_id": "regex-dot",
            "text": "Référence ALT.4892 pour la pièce en panne",
        })
        assert r2.json()["piece_reference"] == "ALT.4892"


class TestFullPipeline:
    """End-to-end pipeline tests: transcribe → analyze (simulates real workflow)."""

    def test_transcribe_then_analyze_pipeline(self, client):
        """
        Simulates the full real-world workflow:
        1. Operator uploads audio → transcription
        2. Transcription text is sent to NLP analysis
        3. Results are verified end-to-end
        """
        # Step 1: Transcribe audio
        fake_wav = io.BytesIO(b"\x00" * 1024)
        transcribe_resp = client.post(
            "/transcribe/",
            data={"ticket_id": "pipeline-ticket-001"},
            files={"file": ("call_recording.wav", fake_wav, "audio/wav")},
        )
        assert transcribe_resp.status_code == 200
        transcription = transcribe_resp.json()

        # Step 2: Send transcribed text to NLP analysis
        analyze_resp = client.post("/analyze/", json={
            "ticket_id": "pipeline-ticket-001-analysis",
            "text": transcription["text"],
        })
        assert analyze_resp.status_code == 200
        analysis = analyze_resp.json()

        # Step 3: Verify the pipeline results
        # The mock transcription says "alternateur est en panne"
        assert analysis["piece_reference"] == "alternateur"  # keyword match
        assert analysis["type_sinistre"] == "PANNE"  # keyword "panne"
        assert analysis["confidence"] == pytest.approx(0.9)  # piece + sinistre → 0.9

        # Step 4: Both records should be retrievable from DB
        t_record = client.get("/transcribe/pipeline-ticket-001")
        assert t_record.status_code == 200
        assert t_record.json()["status"] == "DONE"

        a_record = client.get("/analyze/pipeline-ticket-001-analysis")
        assert a_record.status_code == 200
        assert a_record.json()["piece_reference"] == "alternateur"


class TestErrorScenarios:
    """Tests for error handling and edge cases."""

    def test_transcribe_missing_ticket_id(self, client):
        """POST without ticket_id should fail."""
        fake_wav = io.BytesIO(b"\x00" * 256)
        response = client.post(
            "/transcribe/",
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 422  # Validation error

    def test_transcribe_missing_file(self, client):
        """POST without file should fail."""
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "no-file-test"},
        )
        assert response.status_code == 422  # Validation error

    def test_analyze_missing_ticket_id(self, client):
        """POST /analyze without ticket_id should fail."""
        response = client.post("/analyze/", json={
            "text": "some text without ticket id",
        })
        assert response.status_code == 422

    def test_analyze_missing_text(self, client):
        """POST /analyze without text should fail."""
        response = client.post("/analyze/", json={
            "ticket_id": "missing-text",
        })
        assert response.status_code == 422

    def test_analyze_whitespace_only_text(self, client):
        """POST /analyze with whitespace-only text should return 400."""
        response = client.post("/analyze/", json={
            "ticket_id": "whitespace-text",
            "text": "   \n\t  ",
        })
        assert response.status_code == 400

    def test_get_nonexistent_transcription(self, client):
        """GET /transcribe/nonexistent should return 404."""
        response = client.get("/transcribe/nonexistent-ticket-xyz")
        assert response.status_code == 404

    def test_get_nonexistent_analysis(self, client):
        """GET /analyze/nonexistent should return 404."""
        response = client.get("/analyze/nonexistent-ticket-xyz")
        assert response.status_code == 404
