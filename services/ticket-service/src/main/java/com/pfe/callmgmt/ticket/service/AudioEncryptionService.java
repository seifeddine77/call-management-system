package com.pfe.callmgmt.ticket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service de chiffrement AES-256-GCM pour les fichiers audio (RGPD).
 * <p>
 * Les fichiers audio sont chiffrés avant stockage sur disque et déchiffrés
 * à la demande. La clé est configurée via la variable d'environnement
 * AUDIO_ENCRYPTION_KEY (Base64-encoded, 32 bytes).
 * <p>
 * Format du fichier chiffré : [12 bytes IV][ciphertext + tag]
 */
@Slf4j
@Service
public class AudioEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SecretKey secretKey;
    private final boolean enabled;
    // SonarCloud S2119 fix — reuse SecureRandom instance
    private final SecureRandom secureRandom = new SecureRandom();

    public AudioEncryptionService(
            @Value("${audio.encryption.key:}") String base64Key) {
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "AUDIO_ENCRYPTION_KEY must be 32 bytes (256 bits), got " + keyBytes.length);
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.enabled = true;
            log.info("🔐 Audio encryption enabled (AES-256-GCM)");
        } else {
            this.secretKey = null;
            this.enabled = false;
            log.warn("⚠️ Audio encryption DISABLED — set AUDIO_ENCRYPTION_KEY to enable");
        }
    }

    /**
     * @return true si le chiffrement est activé (clé configurée)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Chiffre les données audio avec AES-256-GCM.
     *
     * @param plainData données audio en clair
     * @return données chiffrées [IV || ciphertext || tag]
     */
    public byte[] encrypt(byte[] plainData) {
        if (!enabled) {
            return plainData; // Passthrough si pas de clé configurée
        }
        try {
            // Générer un IV aléatoire unique
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainData);

            // Concaténer : IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            log.debug("🔐 Audio encrypted: {} bytes → {} bytes", plainData.length, buffer.capacity());
            return buffer.array();
        } catch (Exception e) {
            log.error("❌ Audio encryption failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Échec du chiffrement audio", e);
        }
    }

    /**
     * Déchiffre les données audio chiffrées avec AES-256-GCM.
     *
     * @param encryptedData données chiffrées [IV || ciphertext || tag]
     * @return données audio en clair
     */
    public byte[] decrypt(byte[] encryptedData) {
        if (!enabled) {
            return encryptedData; // Passthrough si pas de clé configurée
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            // Extraire IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            // Extraire ciphertext
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainData = cipher.doFinal(cipherText);
            log.debug("🔓 Audio decrypted: {} bytes → {} bytes", encryptedData.length, plainData.length);
            return plainData;
        } catch (Exception e) {
            log.error("❌ Audio decryption failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Échec du déchiffrement audio", e);
        }
    }
}
