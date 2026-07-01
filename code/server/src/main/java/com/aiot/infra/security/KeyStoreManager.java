package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * 密钥管理器。
 * <p>
 * 基于 Java KeyStore 本地存储主密钥。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.2
 * </p>
 */
@Component
public class KeyStoreManager {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreManager.class);

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String DEFAULT_KEYSTORE_PATH = "data/aiot/keystore.p12";
    private static final String DEFAULT_PASSWORD = "aiot-keystore-password";

    private final String keyStorePath;
    private final char[] password;
    private KeyStore keyStore;

    public KeyStoreManager() {
        this(DEFAULT_KEYSTORE_PATH, DEFAULT_PASSWORD);
    }

    public KeyStoreManager(String keyStorePath, String password) {
        this.keyStorePath = keyStorePath;
        this.password = password.toCharArray();
        initKeyStore();
    }

    /**
     * 初始化 KeyStore。
     */
    private void initKeyStore() {
        try {
            keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            Path path = Paths.get(keyStorePath);

            if (Files.exists(path)) {
                try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                    keyStore.load(fis, password);
                    log.info("KeyStore loaded from: {}", keyStorePath);
                }
            } else {
                // 创建新的 KeyStore
                Files.createDirectories(path.getParent());
                keyStore.load(null, password);
                try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
                    keyStore.store(fos, password);
                }
                log.info("KeyStore created at: {}", keyStorePath);
            }
        } catch (Exception e) {
            log.error("Failed to initialize KeyStore: {}", e.getMessage(), e);
            throw new RuntimeException("KeyStore initialization failed", e);
        }
    }

    /**
     * 存储密钥。
     *
     * @param alias      密钥别名
     * @param keyBase64  Base64 编码的密钥
     * @throws KeyStoreException 存储失败
     */
    public void storeKey(String alias, String keyBase64) throws KeyStoreException {
        try {
            KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(
                    new javax.crypto.spec.SecretKeySpec(
                            java.util.Base64.getDecoder().decode(keyBase64), "AES"));
            KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(password);
            keyStore.setEntry(alias, entry, param);

            // 持久化
            try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
                keyStore.store(fos, password);
            }

            log.info("Key stored: alias={}", alias);
        } catch (Exception e) {
            log.error("Failed to store key: alias={}, error={}", alias, e.getMessage(), e);
            throw new KeyStoreException("Key storage failed", e);
        }
    }

    /**
     * 读取密钥。
     *
     * @param alias 密钥别名
     * @return Base64 编码的密钥
     * @throws KeyStoreException 读取失败
     */
    public String getKey(String alias) throws KeyStoreException {
        try {
            KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(password);
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, param);

            if (entry == null) {
                throw new KeyStoreException("Key not found: " + alias);
            }

            byte[] keyBytes = entry.getSecretKey().getEncoded();
            return java.util.Base64.getEncoder().encodeToString(keyBytes);
        } catch (Exception e) {
            log.error("Failed to get key: alias={}, error={}", alias, e.getMessage(), e);
            throw new KeyStoreException("Key retrieval failed", e);
        }
    }

    /**
     * 检查密钥是否存在。
     *
     * @param alias 密钥别名
     * @return 如果存在则返回 true
     */
    public boolean hasKey(String alias) {
        try {
            return keyStore.containsAlias(alias);
        } catch (Exception e) {
            log.error("Failed to check key existence: alias={}, error={}", alias, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除密钥。
     *
     * @param alias 密钥别名
     * @throws KeyStoreException 删除失败
     */
    public void deleteKey(String alias) throws KeyStoreException {
        try {
            keyStore.deleteEntry(alias);

            // 持久化
            try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
                keyStore.store(fos, password);
            }

            log.info("Key deleted: alias={}", alias);
        } catch (Exception e) {
            log.error("Failed to delete key: alias={}, error={}", alias, e.getMessage(), e);
            throw new KeyStoreException("Key deletion failed", e);
        }
    }

    /**
     * 异常类。
     */
    public static class KeyStoreException extends Exception {
        public KeyStoreException(String message) {
            super(message);
        }

        public KeyStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
