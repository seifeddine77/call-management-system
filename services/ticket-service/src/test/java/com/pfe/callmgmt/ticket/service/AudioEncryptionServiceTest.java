package com.pfe.callmgmt.ticket.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AudioEncryptionService")
class AudioEncryptionServiceTest {

    // SonarCloud S2119 fix — reuse SecureRandom instance
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Génère une clé AES-256 aléatoire pour les tests.
     */
    private String generateTestKey() {
        byte[] key = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    // ========================================
    // ENCRYPTION ENABLED
    // ========================================
    @Nested
    @DisplayName("Avec clé configurée")
    class WithKeyTests {

        @Test
        @DisplayName("encrypt puis decrypt retourne les données originales")
        void encryptDecrypt_roundTrip() {
            String key = generateTestKey();
            AudioEncryptionService service = new AudioEncryptionService(key);

            byte[] original = "Ceci est un fichier audio simulé pour le test RGPD".getBytes();
            byte[] encrypted = service.encrypt(original);
            byte[] decrypted = service.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Les données chiffrées sont différentes des données en clair")
        void encrypt_producesDifferentOutput() {
            String key = generateTestKey();
            AudioEncryptionService service = new AudioEncryptionService(key);

            byte[] original = "Données audio confidentielles".getBytes();
            byte[] encrypted = service.encrypt(original);

            assertThat(encrypted).isNotEqualTo(original);
            assertThat(encrypted.length).isGreaterThan(original.length); // IV + tag overhead
        }

        @Test
        @DisplayName("Deux chiffrements du même contenu produisent des résultats différents (IV unique)")
        void encrypt_uniqueIVPerCall() {
            String key = generateTestKey();
            AudioEncryptionService service = new AudioEncryptionService(key);

            byte[] original = "Même contenu".getBytes();
            byte[] encrypted1 = service.encrypt(original);
            byte[] encrypted2 = service.encrypt(original);

            assertThat(encrypted1).isNotEqualTo(encrypted2); // IV différent à chaque appel
        }

        @Test
        @DisplayName("isEnabled retourne true quand la clé est configurée")
        void isEnabled_withKey_returnsTrue() {
            AudioEncryptionService service = new AudioEncryptionService(generateTestKey());
            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Déchiffrement avec mauvaise clé échoue")
        void decrypt_wrongKey_fails() {
            AudioEncryptionService service1 = new AudioEncryptionService(generateTestKey());
            AudioEncryptionService service2 = new AudioEncryptionService(generateTestKey());

            byte[] encrypted = service1.encrypt("Secret data".getBytes());

            assertThatThrownBy(() -> service2.decrypt(encrypted))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ========================================
    // ENCRYPTION DISABLED
    // ========================================
    @Nested
    @DisplayName("Sans clé (désactivé)")
    class WithoutKeyTests {

        @Test
        @DisplayName("encrypt retourne les données en clair (passthrough)")
        void encrypt_noKey_passthrough() {
            AudioEncryptionService service = new AudioEncryptionService("");

            byte[] original = "Audio en clair".getBytes();
            byte[] result = service.encrypt(original);

            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("decrypt retourne les données en clair (passthrough)")
        void decrypt_noKey_passthrough() {
            AudioEncryptionService service = new AudioEncryptionService(null);

            byte[] original = "Audio en clair".getBytes();
            byte[] result = service.decrypt(original);

            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("isEnabled retourne false quand pas de clé")
        void isEnabled_noKey_returnsFalse() {
            AudioEncryptionService service = new AudioEncryptionService("");
            assertThat(service.isEnabled()).isFalse();
        }
    }

    // ========================================
    // VALIDATION
    // ========================================
    @Nested
    @DisplayName("Validation de la clé")
    class KeyValidationTests {

        @Test
        @DisplayName("Clé de taille invalide (16 bytes au lieu de 32) lance exception")
        void constructor_invalidKeySize_throws() {
            byte[] shortKey = new byte[16]; // 128 bits — trop court
            SECURE_RANDOM.nextBytes(shortKey);
            String base64 = Base64.getEncoder().encodeToString(shortKey);

            assertThatThrownBy(() -> new AudioEncryptionService(base64))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }
    }
}
