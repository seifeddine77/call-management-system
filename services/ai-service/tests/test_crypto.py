"""
Unit tests for AudioCrypto module.
"""
import base64
import os
import pytest
from app.core.crypto import AudioCrypto


class TestAudioCrypto:
    """Test suite for AES-256-GCM audio encryption and decryption."""

    @pytest.fixture
    def random_base64_key(self):
        """Generate a random valid 32-byte key base64-encoded."""
        raw_key = os.urandom(32)
        return base64.b64encode(raw_key).decode("utf-8")

    def test_init_valid_key(self, random_base64_key):
        """Should initialize successfully with a valid 32-byte base64 key."""
        crypto = AudioCrypto(random_base64_key)
        assert crypto.is_enabled() is True
        assert len(crypto.key_bytes) == 32

    def test_init_invalid_key_size(self):
        """Should raise ValueError if the key is not 32 bytes (256 bits)."""
        # 16 bytes key (128 bits)
        invalid_key = base64.b64encode(os.urandom(16)).decode("utf-8")
        with pytest.raises(ValueError, match="32 bytes"):
            AudioCrypto(invalid_key)

    def test_init_invalid_base64(self):
        """Should raise ValueError if the key is not valid base64."""
        with pytest.raises(ValueError):
            AudioCrypto("invalid_base64_string_!!!!")

    def test_init_empty_or_none(self):
        """Should remain disabled if key is empty or None."""
        crypto1 = AudioCrypto("")
        assert crypto1.is_enabled() is False

        crypto2 = AudioCrypto(None)
        assert crypto2.is_enabled() is False

    def test_encrypt_decrypt_roundtrip(self, random_base64_key):
        """Should successfully encrypt and decrypt raw bytes."""
        crypto = AudioCrypto(random_base64_key)
        original_data = b"This is a secret audio recording that must be encrypted under GDPR."

        # Encrypt
        encrypted = crypto.encrypt(original_data)
        assert encrypted != original_data
        assert len(encrypted) == len(original_data) + 12 + 16  # IV + GCM tag overhead

        # Decrypt
        decrypted = crypto.decrypt(encrypted)
        assert decrypted == original_data

    def test_encrypt_decrypt_different_results_for_same_plaintext(self, random_base64_key):
        """Should produce different ciphertexts for the same plaintext due to random IV."""
        crypto = AudioCrypto(random_base64_key)
        original_data = b"Same content"

        encrypted1 = crypto.encrypt(original_data)
        encrypted2 = crypto.encrypt(original_data)

        assert encrypted1 != encrypted2

    def test_decrypt_tampered_data_fails(self, random_base64_key):
        """Should raise ValueError if the encrypted data or tag is tampered with."""
        crypto = AudioCrypto(random_base64_key)
        original_data = b"Sensitive information"

        encrypted = bytearray(crypto.encrypt(original_data))
        # Tamper with ciphertext byte
        encrypted[-5] ^= 0x55

        with pytest.raises(ValueError, match="Decryption failed"):
            crypto.decrypt(bytes(encrypted))

    def test_passthrough_when_disabled(self):
        """Should bypass encryption/decryption (passthrough) when disabled."""
        crypto = AudioCrypto("")
        assert crypto.is_enabled() is False

        original_data = b"Plaintext audio content"

        # Encrypt returns same data
        encrypted = crypto.encrypt(original_data)
        assert encrypted == original_data

        # Decrypt returns same data
        decrypted = crypto.decrypt(encrypted)
        assert decrypted == original_data
