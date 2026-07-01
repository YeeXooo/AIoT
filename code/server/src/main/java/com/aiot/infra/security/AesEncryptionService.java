package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密服务。
 * <p>
 * 提供认证加密（AEAD），防篡改。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.2
 * </p>
 */
@Component
public class AesEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;

    /**
     * 生成 AES-256 密钥。
     *
     * @return Base64 编码的密钥
     * @throws EncryptionException 密钥生成失败
     */
    public String generateKey() throws EncryptionException {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(KEY_LENGTH);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            log.error("Failed to generate AES key: {}", e.getMessage(), e);
            throw new EncryptionException("Key generation failed", e);
        }
    }

    /**
     * 加密数据。
     *
     * @param plaintext 明文
     * @param keyBase64 Base64 编码的密钥
     * @return Base64 编码的密文（包含 IV）
     * @throws EncryptionException 加密失败
     */
    public String encrypt(String plaintext, String keyBase64) throws EncryptionException {
        try {
            SecretKey key = decodeKey(keyBase64);
            byte[] iv = generateIv();

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // IV + 密文
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt data: {}", e.getMessage(), e);
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * 解密数据。
     *
     * @param ciphertextBase64 Base64 编码的密文（包含 IV）
     * @param keyBase64        Base64 编码的密钥
     * @return 明文
     * @throws EncryptionException 解密失败
     */
    public String decrypt(String ciphertextBase64, String keyBase64) throws EncryptionException {
        try {
            SecretKey key = decodeKey(keyBase64);
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);

            // 提取 IV
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // 提取密文
            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            log.error("Failed to decrypt data: {}", e.getMessage(), e);
            throw new EncryptionException("Decryption failed", e);
        }
    }

    /**
     * 解码 Base64 密钥。
     */
    private SecretKey decodeKey(String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 生成随机 IV。
     */
    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * 加解密异常。
     */
    public static class EncryptionException extends Exception {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
