package com.aiot.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockSecondaryAuthProvider 单元测试。
 */
class MockSecondaryAuthProviderTest {

    private MockSecondaryAuthProvider authProvider;

    @BeforeEach
    void setUp() {
        authProvider = new MockSecondaryAuthProvider();
    }

    @Test
    void verify_withValidCode_shouldReturnSuccess() {
        SecondaryAuthProvider.AuthResult result = authProvider.verify("123456", "REMOTE_TALK");
        assertTrue(result.isSuccess());
    }

    @Test
    void verify_withInvalidCode_shouldReturnFailure() {
        SecondaryAuthProvider.AuthResult result = authProvider.verify("000000", "REMOTE_TALK");
        assertFalse(result.isSuccess());
        assertEquals("Invalid verification code", result.message());
    }

    @Test
    void requiresSecondaryAuth_normalOperation_shouldReturnTrue() {
        boolean required = authProvider.requiresSecondaryAuth("REMOTE_TALK", "NORMAL");
        assertTrue(required);
    }

    @Test
    void requiresSecondaryAuth_emergencyScenario_shouldReturnFalse() {
        boolean required = authProvider.requiresSecondaryAuth("REMOTE_TALK", "EMERGENCY_ACTIVATED");
        assertFalse(required);
    }
}
