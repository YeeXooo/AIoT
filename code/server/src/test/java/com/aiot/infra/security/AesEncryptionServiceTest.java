package com.aiot.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AesEncryptionService 单元测试。
 */
class AesEncryptionServiceTest {

    private AesEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionService();
    }

    @Test
    void generateKey_shouldReturnBase64Key() throws Exception {
        String key = encryptionService.generateKey();
        assertNotNull(key);
        assertFalse(key.isEmpty());
        // Base64 编码的 AES-256 密钥应该是 44 个字符
        assertEquals(44, key.length());
    }

    @Test
    void encryptAndDecrypt_shouldWork() throws Exception {
        String key = encryptionService.generateKey();
        String plaintext = "Hello, World!";

        String ciphertext = encryptionService.encrypt(plaintext, key);
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);

        String decrypted = encryptionService.decrypt(ciphertext, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertextForSamePlaintext() throws Exception {
        String key = encryptionService.generateKey();
        String plaintext = "Hello, World!";

        String ciphertext1 = encryptionService.encrypt(plaintext, key);
        String ciphertext2 = encryptionService.encrypt(plaintext, key);

        // 由于 IV 不同，密文应该不同
        assertNotEquals(ciphertext1, ciphertext2);
    }

    @Test
    void decrypt_withWrongKey_shouldThrowException() throws Exception {
        String key1 = encryptionService.generateKey();
        String key2 = encryptionService.generateKey();
        String plaintext = "Hello, World!";

        String ciphertext = encryptionService.encrypt(plaintext, key2);

        assertThrows(AesEncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt(ciphertext, key1));
    }

    @Test
    void encrypt_withDifferentKeys_shouldProduceDifferentCiphertext() throws Exception {
        String key1 = encryptionService.generateKey();
        String key2 = encryptionService.generateKey();
        String plaintext = "Hello, World!";

        String ciphertext1 = encryptionService.encrypt(plaintext, key1);
        String ciphertext2 = encryptionService.encrypt(plaintext, key2);

        assertNotEquals(ciphertext1, ciphertext2);
    }
}
