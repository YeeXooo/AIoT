package com.aiot.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全模块 Spring 配置。
 * <p>
 * 装配 AES-256-GCM 加密工具和 KeyStore 密钥管理器实例。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6
 * </p>
 */
@Configuration
public class SecurityConfig {

    private final SecurityProperties properties;

    public SecurityConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public AesGcmEncryption aesGcmEncryption() {
        return new AesGcmEncryption(
                properties.getCipherAlgorithm(),
                properties.getGcmTagLengthBits(),
                properties.getIvLengthBytes());
    }

    @Bean
    public KeyStoreKeyManager keyStoreKeyManager(AesGcmEncryption aesGcm) {
        return new KeyStoreKeyManager(properties, aesGcm);
    }
}
