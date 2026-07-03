package com.aiot.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionTest {

    private AesGcmEncryption aesGcm;
    private byte[] key;

    @BeforeEach
    void setUp() {
        aesGcm = new AesGcmEncryption("AES/GCM/NoPadding", 128, 12);
        key = aesGcm.generateKey();
    }

    @Test
    void encryptDecryptRoundtrip() {
        byte[] plaintext = "Hello, AIoT!".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);
        byte[] decrypted = aesGcm.decrypt(result.combined(), key);

        assertArrayEquals(plaintext, decrypted);
        assertNotNull(result.iv());
        assertEquals(12, result.iv().length);
    }

    @Test
    void encryptDecryptEmptyArray() {
        byte[] plaintext = new byte[0];
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);
        byte[] decrypted = aesGcm.decrypt(result.combined(), key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecryptLargePayload() {
        byte[] plaintext = new byte[64 * 1024];
        new SecureRandom().nextBytes(plaintext);
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);
        byte[] decrypted = aesGcm.decrypt(result.combined(), key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptNullThrowsException() {
        assertThrows(SecurityException.class, () -> aesGcm.encrypt(null, key));
    }

    @Test
    void decryptNullThrowsException() {
        assertThrows(SecurityException.class, () -> aesGcm.decrypt(null, key));
    }

    @Test
    void decryptWithNullKeyThrowsException() {
        byte[] plaintext = "test".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);

        assertThrows(SecurityException.class, () -> aesGcm.decrypt(result.combined(), null));
    }

    @Test
    void encryptWithNullKeyThrowsException() {
        assertThrows(SecurityException.class, () -> aesGcm.encrypt("test".getBytes(), null));
    }

    @Test
    void decryptTamperedCiphertextThrowsException() {
        byte[] plaintext = "sensitive data".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);

        byte[] tampered = result.combined().clone();
        tampered[tampered.length - 1] ^= 0xFF;

        assertThrows(SecurityException.class, () -> aesGcm.decrypt(tampered, key));
    }

    @Test
    void decryptTruncatedCiphertextThrowsException() {
        byte[] plaintext = "sensitive data".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);

        byte[] truncated = new byte[3];
        System.arraycopy(result.combined(), 0, truncated, 0, 3);

        assertThrows(SecurityException.class, () -> aesGcm.decrypt(truncated, key));
    }

    @Test
    void decryptWrongKeyThrowsException() {
        byte[] plaintext = "sensitive data".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);

        byte[] wrongKey = aesGcm.generateKey();

        assertThrows(SecurityException.class, () -> aesGcm.decrypt(result.combined(), wrongKey));
    }

    @Test
    void generateKeyReturns32Bytes() {
        byte[] generated = aesGcm.generateKey();
        assertNotNull(generated);
        assertEquals(32, generated.length);
    }

    @Test
    void generateKeyProducesDifferentKeys() {
        byte[] key1 = aesGcm.generateKey();
        byte[] key2 = aesGcm.generateKey();
        assertFalse(java.util.Arrays.equals(key1, key2));
    }

    @Test
    void constructorWithCustomParameters() {
        AesGcmEncryption custom = new AesGcmEncryption("AES/GCM/NoPadding", 128, 12);
        byte[] plaintext = "custom params".getBytes();
        byte[] k = custom.generateKey();
        AesGcmEncryption.EncryptionResult result = custom.encrypt(plaintext, k);
        byte[] decrypted = custom.decrypt(result.combined(), k);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptionResultContainsIvPlusCiphertext() {
        byte[] plaintext = "structure test".getBytes();
        AesGcmEncryption.EncryptionResult result = aesGcm.encrypt(plaintext, key);

        assertTrue(result.combined().length > 12);
        assertEquals(12, result.iv().length);

        byte[] extractedIv = new byte[12];
        System.arraycopy(result.combined(), 0, extractedIv, 0, 12);
        assertArrayEquals(result.iv(), extractedIv);
    }
}
