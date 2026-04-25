"""
Tests for transcription router — 4 test cases.
"""
import io


class TestTranscriptionValidation:
    """Tests for audio upload validation."""

    def test_transcribe_invalid_file_type(self, client):
        """Upload a PDF file should return 400."""
        fake_pdf = io.BytesIO(b"%PDF-1.4 fake content")
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-001"},
            files={"file": ("document.pdf", fake_pdf, "application/pdf")},
        )
        assert response.status_code == 400
        assert "non supporté" in response.json()["detail"]

    def test_transcribe_too_large(self, client):
        """File > 50MB should return 400."""
        # Create a fake file just over 50MB
        large_content = b"\x00" * (50 * 1024 * 1024 + 1)
        fake_wav = io.BytesIO(large_content)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-too-large"},
            files={"file": ("large.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 400
        assert "volumineux" in response.json()["detail"]

    def test_transcribe_success(self, client):
        """Valid WAV file with mock Whisper should return 200 + TranscribeResponse."""
        # Create a minimal fake WAV file (just bytes, Whisper is mocked)
        fake_wav = io.BytesIO(b"\x00" * 1024)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-002"},
            files={"file": ("recording.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["ticket_id"] == "ticket-002"
        assert "alternateur" in data["text"]
        assert data["confidence"] > 0
        assert data["duration_seconds"] > 0
        assert len(data["segments"]) == 2

    def test_get_transcription_not_found(self, client):
        """GET /transcribe/fake-id should return 404."""
        response = client.get("/transcribe/fake-ticket-id")
        assert response.status_code == 404

    def test_get_transcription_after_create(self, client):
        """After transcribing, GET should return the record."""
        # Create first
        fake_wav = io.BytesIO(b"\x00" * 512)
        client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-003"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        # Then retrieve
        response = client.get("/transcribe/ticket-003")
        assert response.status_code == 200
        assert response.json()["ticket_id"] == "ticket-003"
        assert response.json()["status"] == "DONE"


class TestTranscriptionNoWhisper:
    """Tests when Whisper model is not available."""

    def test_transcribe_no_whisper(self, client_no_whisper):
        """Upload when Whisper is not loaded should return 503."""
        fake_wav = io.BytesIO(b"\x00" * 512)
        response = client_no_whisper.post(
            "/transcribe/",
            data={"ticket_id": "ticket-no-whisper"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 503
        assert "Whisper" in response.json()["detail"]
