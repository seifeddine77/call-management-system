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


class TestTranscriptionEdgeCases:
    """Tests for various edge cases and error conditions."""

    def test_transcribe_conflict(self, client):
        """Trying to transcribe a ticket that already exists returns 409."""
        fake_wav = io.BytesIO(b"\x00" * 256)
        # First request
        response1 = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-conflict"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response1.status_code == 200

        # Second request with same ticket_id
        response2 = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-conflict"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response2.status_code == 409
        assert "déjà" in response2.json()["detail"]

    def test_transcribe_path_traversal(self, client):
        """Providing a ticket_id with path traversal should return 400."""
        fake_wav = io.BytesIO(b"\x00" * 256)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "../outside-dir"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 400
        assert "traversal" in response.json()["detail"]

    def test_transcribe_whisper_fails(self, client):
        """When Whisper fails, DB record should be FAILED and return 500."""
        class FailWhisper:
            def transcribe(self, file_path, language="fr", fp16=False):
                raise RuntimeError("Whisper crash")

        client.app.state.whisper_model = FailWhisper()
        fake_wav = io.BytesIO(b"\x00" * 256)
        response = client.post(
            "/transcribe/",
            data={"ticket_id": "ticket-whisper-fails"},
            files={"file": ("test.wav", fake_wav, "audio/wav")},
        )
        assert response.status_code == 500
        assert "Erreur" in response.json()["detail"]

        # Check DB record status
        get_resp = client.get("/transcribe/ticket-whisper-fails")
        assert get_resp.status_code == 200
        assert get_resp.json()["status"] == "FAILED"


class TestQuickTranscription:
    """Tests for the POST /transcribe/quick endpoint."""

    def test_quick_transcribe_success(self, client):
        """Quick transcribe returns transcription text without DB record."""
        fake_wav = io.BytesIO(b"\x00" * 512)
        response = client.post(
            "/transcribe/quick",
            files={"file": ("whatsapp.ogg", fake_wav, "audio/ogg")},
        )
        assert response.status_code == 200
        assert "alternateur" in response.json()["text"]
        assert "duration_seconds" in response.json()

    def test_quick_transcribe_no_whisper(self, client_no_whisper):
        """Quick transcribe returns 503 when Whisper is not loaded."""
        fake_wav = io.BytesIO(b"\x00" * 512)
        response = client_no_whisper.post(
            "/transcribe/quick",
            files={"file": ("whatsapp.ogg", fake_wav, "audio/ogg")},
        )
        assert response.status_code == 503

    def test_quick_transcribe_too_large(self, client):
        """Quick transcribe returns 400 when file is > 50MB."""
        large_content = b"\x00" * (50 * 1024 * 1024 + 1)
        fake_wav = io.BytesIO(large_content)
        response = client.post(
            "/transcribe/quick",
            files={"file": ("whatsapp.ogg", fake_wav, "audio/ogg")},
        )
        assert response.status_code == 400
        assert "volumineux" in response.json()["detail"]

    def test_quick_transcribe_whisper_fails(self, client):
        """Quick transcribe returns 500 when Whisper fails."""
        class FailWhisper:
            def transcribe(self, file_path, language="fr", fp16=False):
                raise RuntimeError("Whisper crash")

        client.app.state.whisper_model = FailWhisper()
        fake_wav = io.BytesIO(b"\x00" * 256)
        response = client.post(
            "/transcribe/quick",
            files={"file": ("whatsapp.ogg", fake_wav, "audio/ogg")},
        )
        assert response.status_code == 500
        assert "Erreur" in response.json()["detail"]
