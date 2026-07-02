package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/**
 * 基于 Java KeyStore 的本地密钥管理。
 * <p>
 * 使用 PKCS12 KeyStore 在本地文件系统中安全存储主密钥，
 * 替代华为云 DEW 远程密钥管理。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.2
 * </p>
 */
public class KeyStoreKeyManager {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreKeyManager.class);

    private final SecurityProperties properties;
    private final AesGcmEncryption aesGcm;
    private volatile SecretKey cachedMasterKey;

    public KeyStoreKeyManager(SecurityProperties properties, AesGcmEncryption aesGcm) {
        this.properties = properties;
        this.aesGcm = aesGcm;
    }

    /**
     * 获取或初始化主密钥。
     * <p>
     * 首次调用时若 KeyStore 不存在则自动创建并生成主密钥。
     * 后续调用从 KeyStore 加载已存在的主密钥。
     * </p>
     *
     * @return AES-256 主密钥（明文，仅在内存中）
     */
    public SecretKey loadOrCreateMasterKey() {
        if (cachedMasterKey != null) {
            return cachedMasterKey;
        }

        synchronized (this) {
            if (cachedMasterKey != null) {
                return cachedMasterKey;
            }

            ensureParentDirectory();

            try {
                File ksFile = new File(properties.getKeyStorePath());
                KeyStore keyStore = KeyStore.getInstance(properties.getKeyStoreType());
                char[] ksPassword = properties.getKeyStorePassword().toCharArray();
                char[] keyPassword = properties.getMasterKeyPassword().toCharArray();

                if (ksFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(ksFile)) {
                        keyStore.load(fis, ksPassword);
                    }
                    log.info("KeyStore 已加载: {}", properties.getKeyStorePath());
                } else {
                    keyStore.load(null, ksPassword);
                    log.info("KeyStore 已创建: {}", properties.getKeyStorePath());
                }

                KeyStore.SecretKeyEntry entry =
                        (KeyStore.SecretKeyEntry) keyStore.getEntry(
                                properties.getMasterKeyAlias(),
                                new KeyStore.PasswordProtection(keyPassword));

                if (entry == null) {
                    byte[] keyBytes = aesGcm.generateKey();
                    SecretKey newKey = new SecretKeySpec(keyBytes, "AES");
                    keyStore.setEntry(
                            properties.getMasterKeyAlias(),
                            new KeyStore.SecretKeyEntry(newKey),
                            new KeyStore.PasswordProtection(keyPassword));
                    try (FileOutputStream fos = new FileOutputStream(ksFile)) {
                        keyStore.store(fos, ksPassword);
                    }
                    log.info("主密钥已生成并持久化到 KeyStore");
                    cachedMasterKey = newKey;
                } else {
                    cachedMasterKey = entry.getSecretKey();
                    log.debug("主密钥已从 KeyStore 加载");
                }

                return cachedMasterKey;
            } catch (Exception e) {
                throw new SecurityException("KeyStore 操作失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 获取主密钥的原始字节。
     * <p>
     * 用于 AES-256-GCM 加密/解密操作。
     * </p>
     */
    public byte[] getMasterKeyBytes() {
        return loadOrCreateMasterKey().getEncoded();
    }

    /**
     * 清除内存中的主密钥缓存。
     * <p>
     * 调用后下次获取主密钥将从 KeyStore 重新加载。
     * </p>
     */
    public void clearCache() {
        synchronized (this) {
            cachedMasterKey = null;
        }
    }

    private void ensureParentDirectory() {
        try {
            Path parent = Path.of(properties.getKeyStorePath()).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new SecurityException("无法创建 KeyStore 目录: " + e.getMessage(), e);
        }
    }
}
