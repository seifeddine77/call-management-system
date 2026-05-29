"""
AES-256-GCM audio encryption/decryption module for GDPR compliance.
Matches Java AudioEncryptionService exactly.
"""
import base64
import logging
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

logger = logging.getLogger("ai-service.crypto")


class AudioCrypto:
    """
    AES-256-GCM audio encryption and decryption utility.
    Matches Java service's implementation exactly.
    Encryption format: [12 bytes IV][ciphertext][16 bytes GCM Tag]
    """

    def __init__(self, base64_key: str):
        self.enabled = False
        self.key_bytes = None

        if base64_key and base64_key.strip():
            try:
                self.key_bytes = base64.b64decode(base64_key.strip())
            except Exception as e:
                raise ValueError(f"Invalid base64 key: {e}") from e

            if len(self.key_bytes) != 32:
                raise ValueError(
                    f"AUDIO_ENCRYPTION_KEY must be 32 bytes (256 bits), got {len(self.key_bytes)}"
                )
            self.enabled = True
            logger.info("🔐 Audio encryption/decryption enabled (AES-256-GCM)")
        else:
            logger.warning("⚠️ Audio encryption/decryption DISABLED — AUDIO_ENCRYPTION_KEY not set")

    def is_enabled(self) -> bool:
        """Return whether audio encryption is enabled."""
        return self.enabled

    def encrypt(self, plain_bytes: bytes) -> bytes:
        """
        Encrypt audio data with AES-256-GCM.

        :param plain_bytes: plaintext audio data
        :return: encrypted payload [IV || ciphertext || Tag]
        """
        if not self.enabled or not self.key_bytes:
            return plain_bytes

        try:
            # 12-byte random IV
            iv = os.urandom(12)
            aesgcm = AESGCM(self.key_bytes)
            # aesgcm.encrypt automatically appends the 16-byte tag to the ciphertext
            ciphertext = aesgcm.encrypt(iv, plain_bytes, None)
            logger.debug(f"🔐 Audio encrypted: {len(plain_bytes)} bytes -> {len(iv) + len(ciphertext)} bytes")
            return iv + ciphertext
        except Exception as e:
            logger.error(f"❌ Audio encryption failed: {e}")
            raise RuntimeError(f"Échec du chiffrement audio: {e}") from e

    def decrypt(self, encrypted_bytes: bytes) -> bytes:
        """
        Decrypt audio data with AES-256-GCM.

        :param encrypted_bytes: encrypted payload [IV || ciphertext || Tag]
        :return: decrypted plaintext audio data
        """
        if not self.enabled or not self.key_bytes:
            return encrypted_bytes

        try:
            if len(encrypted_bytes) < 28:  # 12 bytes IV + 16 bytes minimum Tag/ciphertext overhead
                raise ValueError("Data too short to be encrypted")

            iv = encrypted_bytes[:12]
            ciphertext = encrypted_bytes[12:]
            aesgcm = AESGCM(self.key_bytes)
            plain_bytes = aesgcm.decrypt(iv, ciphertext, None)
            logger.debug(f"🔓 Audio decrypted: {len(encrypted_bytes)} bytes -> {len(plain_bytes)} bytes")
            return plain_bytes
        except Exception as e:
            # Raise an explicit exception so calling logic can detect decryption failure
            raise ValueError(f"Decryption failed: {e}") from e
