package com.aiot.interfaces.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttDeviceAuthProviderTest {

    @Mock
    private MqttProperties properties;

    private MqttDeviceAuthProvider authProvider;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getDeviceTokenTtlSec()).thenReturn(3600);
        authProvider = new MqttDeviceAuthProvider(properties);
    }

    @Test
    void registerDevice_shouldAddDevice() {
        authProvider.registerDevice("dev-1", "secret123");

        assertTrue(authProvider.isDeviceRegistered("dev-1"));
        assertEquals(1, authProvider.registeredDeviceCount());
    }

    @Test
    void unregisterDevice_shouldRemoveDevice() {
        authProvider.registerDevice("dev-1", "secret123");
        authProvider.unregisterDevice("dev-1");

        assertFalse(authProvider.isDeviceRegistered("dev-1"));
        assertEquals(0, authProvider.registeredDeviceCount());
    }

    @Test
    void issueDeviceToken_shouldReturnTokenForRegisteredDevice() {
        authProvider.registerDevice("dev-1", "secret123");

        String token = authProvider.issueDeviceToken("dev-1");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void issueDeviceToken_shouldReturnNullForUnregisteredDevice() {
        String token = authProvider.issueDeviceToken("unknown-dev");

        assertNull(token);
    }

    @Test
    void validateDeviceToken_shouldReturnDeviceIdForValidToken() {
        authProvider.registerDevice("dev-1", "secret123");
        String token = authProvider.issueDeviceToken("dev-1");

        String deviceId = authProvider.validateDeviceToken(token);

        assertEquals("dev-1", deviceId);
    }

    @Test
    void validateDeviceToken_shouldReturnNullForInvalidToken() {
        String deviceId = authProvider.validateDeviceToken("invalid-token");

        assertNull(deviceId);
    }

    @Test
    void validateDeviceToken_shouldReturnNullForEmptyString() {
        String deviceId = authProvider.validateDeviceToken("");

        assertNull(deviceId);
    }

    @Test
    void validateDevicePassword_shouldReturnTrueForCorrectPassword() {
        authProvider.registerDevice("dev-1", "secret123");

        assertTrue(authProvider.validateDevicePassword("dev-1", "secret123"));
    }

    @Test
    void validateDevicePassword_shouldReturnFalseForWrongPassword() {
        authProvider.registerDevice("dev-1", "secret123");

        assertFalse(authProvider.validateDevicePassword("dev-1", "wrong-secret"));
    }

    @Test
    void validateDevicePassword_shouldReturnFalseForUnregisteredDevice() {
        assertFalse(authProvider.validateDevicePassword("unknown-dev", "secret"));
    }

    @Test
    void revokeToken_shouldRemoveFromIssuedTokensAndInvalidate() {
        authProvider.registerDevice("dev-1", "secret123");
        String token = authProvider.issueDeviceToken("dev-1");

        authProvider.revokeToken(token);
        authProvider.registerDevice("dev-1", "new-secret");

        assertNull(authProvider.validateDeviceToken(token));
    }

    @Test
    void cleanupExpiredTokens_shouldRemoveExpiredTokens() {
        when(properties.getDeviceTokenTtlSec()).thenReturn(-1);
        authProvider = new MqttDeviceAuthProvider(properties);
        authProvider.registerDevice("dev-1", "secret123");
        String token = authProvider.issueDeviceToken("dev-1");

        int removed = authProvider.cleanupExpiredTokens();

        assertTrue(removed > 0);
        assertNull(authProvider.validateDeviceToken(token));
    }

    @Test
    void isDeviceRegistered_shouldReturnFalseForUnknownDevice() {
        assertFalse(authProvider.isDeviceRegistered("unknown"));
    }

    @Test
    void registeredDeviceCount_shouldReturnZeroInitially() {
        assertEquals(0, authProvider.registeredDeviceCount());
    }

    @Test
    void registerMultipleDevices_shouldTrackAll() {
        authProvider.registerDevice("dev-1", "s1");
        authProvider.registerDevice("dev-2", "s2");
        authProvider.registerDevice("dev-3", "s3");

        assertEquals(3, authProvider.registeredDeviceCount());
        assertTrue(authProvider.isDeviceRegistered("dev-1"));
        assertTrue(authProvider.isDeviceRegistered("dev-2"));
        assertTrue(authProvider.isDeviceRegistered("dev-3"));
    }

    @Test
    void issueDeviceToken_forDifferentDevices_shouldReturnDifferentTokens() {
        authProvider.registerDevice("dev-1", "secret1");
        authProvider.registerDevice("dev-2", "secret2");

        String token1 = authProvider.issueDeviceToken("dev-1");
        String token2 = authProvider.issueDeviceToken("dev-2");

        assertNotEquals(token1, token2);
    }

    @Test
    void validateDeviceToken_shouldRejectAfterSecretChange() {
        authProvider.registerDevice("dev-1", "secret123");
        String token = authProvider.issueDeviceToken("dev-1");

        authProvider.revokeToken(token);
        authProvider.registerDevice("dev-1", "different-secret");

        assertNull(authProvider.validateDeviceToken(token));
    }
}
