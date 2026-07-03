package com.aiot.infra.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPropertiesTest {

    @Test
    void defaultCipherAlgorithm() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("AES/GCM/NoPadding", props.getCipherAlgorithm());
    }

    @Test
    void defaultKeySizeBits() {
        SecurityProperties props = new SecurityProperties();
        assertEquals(256, props.getKeySizeBits());
    }

    @Test
    void defaultGcmTagLengthBits() {
        SecurityProperties props = new SecurityProperties();
        assertEquals(128, props.getGcmTagLengthBits());
    }

    @Test
    void defaultIvLengthBytes() {
        SecurityProperties props = new SecurityProperties();
        assertEquals(12, props.getIvLengthBytes());
    }

    @Test
    void defaultKeyStoreType() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("PKCS12", props.getKeyStoreType());
    }

    @Test
    void defaultKeyStorePath() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("/data/aiot/security/keystore.p12", props.getKeyStorePath());
    }

    @Test
    void defaultKeyStorePassword() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("aiot-keystore-change-me", props.getKeyStorePassword());
    }

    @Test
    void defaultMasterKeyAlias() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("aiot-master-key", props.getMasterKeyAlias());
    }

    @Test
    void defaultMasterKeyPassword() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("aiot-master-key-pwd", props.getMasterKeyPassword());
    }

    @Test
    void defaultVoiceEvidenceRetentionDays() {
        SecurityProperties props = new SecurityProperties();
        assertEquals(90, props.getVoiceEvidenceRetentionDays());
    }

    @Test
    void defaultVoiceEvidenceBasePath() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("/data/aiot/road_rage_evidence", props.getVoiceEvidenceBasePath());
    }

    @Test
    void defaultMockVerificationCode() {
        SecurityProperties props = new SecurityProperties();
        assertEquals("123456", props.getMockVerificationCode());
    }

    @Test
    void defaultMockCodeValiditySeconds() {
        SecurityProperties props = new SecurityProperties();
        assertEquals(300, props.getMockCodeValiditySeconds());
    }

    @Test
    void settersAndGetters() {
        SecurityProperties props = new SecurityProperties();

        props.setCipherAlgorithm("AES/CBC/PKCS5Padding");
        assertEquals("AES/CBC/PKCS5Padding", props.getCipherAlgorithm());

        props.setKeySizeBits(128);
        assertEquals(128, props.getKeySizeBits());

        props.setGcmTagLengthBits(96);
        assertEquals(96, props.getGcmTagLengthBits());

        props.setIvLengthBytes(16);
        assertEquals(16, props.getIvLengthBytes());

        props.setKeyStoreType("JCEKS");
        assertEquals("JCEKS", props.getKeyStoreType());

        props.setKeyStorePath("/custom/path/ks.p12");
        assertEquals("/custom/path/ks.p12", props.getKeyStorePath());

        props.setKeyStorePassword("new-password");
        assertEquals("new-password", props.getKeyStorePassword());

        props.setMasterKeyAlias("custom-alias");
        assertEquals("custom-alias", props.getMasterKeyAlias());

        props.setMasterKeyPassword("custom-key-pwd");
        assertEquals("custom-key-pwd", props.getMasterKeyPassword());

        props.setVoiceEvidenceRetentionDays(180);
        assertEquals(180, props.getVoiceEvidenceRetentionDays());

        props.setVoiceEvidenceBasePath("/custom/evidence");
        assertEquals("/custom/evidence", props.getVoiceEvidenceBasePath());

        props.setMockVerificationCode("654321");
        assertEquals("654321", props.getMockVerificationCode());

        props.setMockCodeValiditySeconds(600);
        assertEquals(600, props.getMockCodeValiditySeconds());
    }
}
