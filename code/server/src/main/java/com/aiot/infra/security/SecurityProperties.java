package com.aiot.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全模块配置。
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "aiot.security")
public class SecurityProperties {

    /** AES 加密算法 */
    private String cipherAlgorithm = "AES/GCM/NoPadding";

    /** AES 密钥长度（bit） */
    private int keySizeBits = 256;

    /** GCM 认证标签长度（bit） */
    private int gcmTagLengthBits = 128;

    /** GCM IV 长度（byte） */
    private int ivLengthBytes = 12;

    /** KeyStore 类型 */
    private String keyStoreType = "PKCS12";

    /** KeyStore 文件路径 */
    private String keyStorePath = "/data/aiot/security/keystore.p12";

    /** KeyStore 密码 */
    private String keyStorePassword = "aiot-keystore-change-me";

    /** 主密钥别名 */
    private String masterKeyAlias = "aiot-master-key";

    /** 主密钥密码 */
    private String masterKeyPassword = "aiot-master-key-pwd";

    /** 语音存证默认保留天数 */
    private int voiceEvidenceRetentionDays = 90;

    /** 语音存证加密存储根目录 */
    private String voiceEvidenceBasePath = "/data/aiot/road_rage_evidence";

    /** Mock 验证码 */
    private String mockVerificationCode = "123456";

    /** Mock 验证码有效期（秒） */
    private int mockCodeValiditySeconds = 300;

    // ── JavaBean getters / setters ──

    public String getCipherAlgorithm() { return cipherAlgorithm; }
    public void setCipherAlgorithm(String cipherAlgorithm) { this.cipherAlgorithm = cipherAlgorithm; }

    public int getKeySizeBits() { return keySizeBits; }
    public void setKeySizeBits(int keySizeBits) { this.keySizeBits = keySizeBits; }

    public int getGcmTagLengthBits() { return gcmTagLengthBits; }
    public void setGcmTagLengthBits(int gcmTagLengthBits) { this.gcmTagLengthBits = gcmTagLengthBits; }

    public int getIvLengthBytes() { return ivLengthBytes; }
    public void setIvLengthBytes(int ivLengthBytes) { this.ivLengthBytes = ivLengthBytes; }

    public String getKeyStoreType() { return keyStoreType; }
    public void setKeyStoreType(String keyStoreType) { this.keyStoreType = keyStoreType; }

    public String getKeyStorePath() { return keyStorePath; }
    public void setKeyStorePath(String keyStorePath) { this.keyStorePath = keyStorePath; }

    public String getKeyStorePassword() { return keyStorePassword; }
    public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }

    public String getMasterKeyAlias() { return masterKeyAlias; }
    public void setMasterKeyAlias(String masterKeyAlias) { this.masterKeyAlias = masterKeyAlias; }

    public String getMasterKeyPassword() { return masterKeyPassword; }
    public void setMasterKeyPassword(String masterKeyPassword) { this.masterKeyPassword = masterKeyPassword; }

    public int getVoiceEvidenceRetentionDays() { return voiceEvidenceRetentionDays; }
    public void setVoiceEvidenceRetentionDays(int voiceEvidenceRetentionDays) { this.voiceEvidenceRetentionDays = voiceEvidenceRetentionDays; }

    public String getVoiceEvidenceBasePath() { return voiceEvidenceBasePath; }
    public void setVoiceEvidenceBasePath(String voiceEvidenceBasePath) { this.voiceEvidenceBasePath = voiceEvidenceBasePath; }

    public String getMockVerificationCode() { return mockVerificationCode; }
    public void setMockVerificationCode(String mockVerificationCode) { this.mockVerificationCode = mockVerificationCode; }

    public int getMockCodeValiditySeconds() { return mockCodeValiditySeconds; }
    public void setMockCodeValiditySeconds(int mockCodeValiditySeconds) { this.mockCodeValiditySeconds = mockCodeValiditySeconds; }
}
