package com.aiot.infra.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AES-256-GCM 认证加密工具。
 * <p>
 * 提供加密/解密能力，用于语音存证、离线消息等敏感数据的本地加密存储。
 * GCM 模式提供 AEAD（认证加密），同时保证机密性和完整性。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.2
 * </p>
 */
public final class AesGcmEncryption {

    private final String cipherAlgorithm;
    private final int tagLengthBits;
    private final int ivLengthBytes;
    private final SecureRandom secureRandom;

    /**
     * @param cipherAlgorithm 密码算法，如 "AES/GCM/NoPadding"
     * @param tagLengthBits   GCM 认证标签长度（bit），通常 128
     * @param ivLengthBytes   IV 长度（byte），GCM 推荐 12
     */
    public AesGcmEncryption(String cipherAlgorithm, int tagLengthBits, int ivLengthBytes) {
        this.cipherAlgorithm = cipherAlgorithm;
        this.tagLengthBits = tagLengthBits;
        this.ivLengthBytes = ivLengthBytes;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文数据。
     *
     * @param plaintext 明文
     * @param key       256-bit AES 密钥
     * @return 加密结果（IV + 密文 + GCM 认证标签 拼接），其中 IV 位于前 {@link #ivLengthBytes} 字节
     */
    public EncryptionResult encrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = new byte[ivLengthBytes];
            secureRandom.nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagLengthBits, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return new EncryptionResult(combined, iv);
        } catch (Exception e) {
            throw new SecurityException("AES-GCM 加密失败", e);
        }
    }

    /**
     * 解密密文数据。
     *
     * @param encryptedData 加密数据（IV + 密文 + GCM 认证标签 拼接）
     * @param key           256-bit AES 密钥
     * @return 明文
     */
    public byte[] decrypt(byte[] encryptedData, byte[] key) {
        try {
            byte[] iv = new byte[ivLengthBytes];
            System.arraycopy(encryptedData, 0, iv, 0, ivLengthBytes);

            byte[] ciphertext = new byte[encryptedData.length - ivLengthBytes];
            System.arraycopy(encryptedData, ivLengthBytes, ciphertext, 0, ciphertext.length);

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagLengthBits, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("AES-GCM 解密失败", e);
        }
    }

    /**
     * 生成随机 AES-256 密钥。
     *
     * @return 32 字节密钥
     */
    public byte[] generateKey() {
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);
        return key;
    }

    /**
     * 加密操作结果，包含 IV + 密文 + 认证标签的拼接数据。
     *
     * @param combined IV + ciphertext + GCM tag 拼接
     * @param iv       本次加密使用的 IV（前 12 字节）
     */
    public record EncryptionResult(byte[] combined, byte[] iv) {}
}
