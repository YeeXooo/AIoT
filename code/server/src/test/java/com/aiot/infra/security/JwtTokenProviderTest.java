package com.aiot.infra.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @Mock
    private KeyStoreKeyManager keyStoreManager;

    @Mock
    private SecurityProperties properties;

    private JwtTokenProvider provider;

    private final byte[] keyBytes = new byte[32];

    @BeforeEach
    void setUp() {
        new SecureRandom().nextBytes(keyBytes);
        when(keyStoreManager.getMasterKeyBytes()).thenReturn(keyBytes);
        provider = new JwtTokenProvider(keyStoreManager, properties);
    }

    @Test
    void createAccessTokenReturnsNonEmptyString() {
        String token = provider.createAccessToken("account-001", "FAMILY");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length >= 2);
    }

    @Test
    void createRefreshTokenReturnsNonEmptyString() {
        String token = provider.createRefreshToken("account-002", "MANAGER");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length >= 2);
    }

    @Test
    void createTokenPairReturnsAllFields() {
        JwtTokenProvider.TokenPair pair = provider.createTokenPair("account-003", "RESCUE");
        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertEquals("Bearer", pair.tokenType());
        assertEquals(3600, pair.expiresIn());
        assertEquals("account-003", pair.accountId());
        assertEquals("RESCUE", pair.role());
        assertNotEquals(pair.accessToken(), pair.refreshToken());
    }

    @Test
    void validateTokenReturnsClaimsForValidToken() {
        String token = provider.createAccessToken("account-004", "FAMILY");
        Claims claims = provider.validateToken(token);

        assertNotNull(claims);
        assertEquals("account-004", claims.getSubject());
        assertEquals("FAMILY", claims.get("role", String.class));
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    void validateTokenReturnsNullForExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(keyStoreManager, properties) {
            @Override
            public String createAccessToken(String accountId, String role) {
                return createExpiredToken(accountId, role);
            }

            private String createExpiredToken(String accountId, String role) {
                java.time.Instant now = java.time.Instant.now();
                java.time.Instant expiration = now.minusSeconds(60);
                java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
                extraClaims.put("role", role);
                extraClaims.put("type", "access");
                return io.jsonwebtoken.Jwts.builder()
                        .claims(extraClaims)
                        .subject(accountId)
                        .issuedAt(java.util.Date.from(now))
                        .expiration(java.util.Date.from(expiration))
                        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes))
                        .compact();
            }
        };
        String token = expiredProvider.createAccessToken("account-005", "FAMILY");
        Claims claims = expiredProvider.validateToken(token);
        assertNull(claims);
    }

    @Test
    void validateTokenReturnsNullForTamperedToken() {
        String token = provider.createAccessToken("account-006", "FAMILY");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";
        Claims claims = provider.validateToken(tampered);
        assertNull(claims);
    }

    @Test
    void validateTokenThrowsForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> provider.validateToken(null));
    }

    @Test
    void validateTokenThrowsForEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> provider.validateToken(""));
    }

    @Test
    void validateTokenReturnsNullForMalformedToken() {
        assertNull(provider.validateToken("not.a.jwt"));
    }

    @Test
    void validateTokenReturnsNullForTokenSignedWithDifferentKey() {
        byte[] otherKeyBytes = new byte[32];
        new SecureRandom().nextBytes(otherKeyBytes);
        String foreignToken = io.jsonwebtoken.Jwts.builder()
                .subject("account-007")
                .claim("role", "FAMILY")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(otherKeyBytes))
                .compact();

        assertNull(provider.validateToken(foreignToken));
    }

    @Test
    void getAccountIdReturnsSubjectForValidToken() {
        String token = provider.createAccessToken("account-008", "MANAGER");
        assertEquals("account-008", provider.getAccountId(token));
    }

    @Test
    void getAccountIdReturnsNullForInvalidToken() {
        assertNull(provider.getAccountId("invalid.token.here"));
    }

    @Test
    void getAccountIdThrowsForNullToken() {
        assertThrows(IllegalArgumentException.class, () -> provider.getAccountId(null));
    }

    @Test
    void getRoleReturnsRoleForValidToken() {
        String token = provider.createAccessToken("account-009", "RESCUE");
        assertEquals("RESCUE", provider.getRole(token));
    }

    @Test
    void getRoleReturnsNullForInvalidToken() {
        assertNull(provider.getRole("bad.token.value"));
    }

    @Test
    void getRoleThrowsForNullToken() {
        assertThrows(IllegalArgumentException.class, () -> provider.getRole(null));
    }

    @Test
    void isValidReturnsTrueForValidToken() {
        String token = provider.createAccessToken("account-010", "FAMILY");
        assertTrue(provider.isValid(token));
    }

    @Test
    void isValidReturnsFalseForInvalidToken() {
        assertFalse(provider.isValid("not.a.valid.token"));
    }

    @Test
    void isValidThrowsForNullToken() {
        assertThrows(IllegalArgumentException.class, () -> provider.isValid(null));
    }

    @Test
    void createAccessTokenContainsDifferentAccounts() {
        String token1 = provider.createAccessToken("account-A", "FAMILY");
        String token2 = provider.createAccessToken("account-B", "FAMILY");

        assertNotEquals(token1, token2);
        assertEquals("account-A", provider.getAccountId(token1));
        assertEquals("account-B", provider.getAccountId(token2));
    }

    @Test
    void accessTokenHasExpirationInFuture() {
        String token = provider.createAccessToken("account-011", "FAMILY");
        Claims claims = provider.validateToken(token);
        assertNotNull(claims);
        Date expiration = claims.getExpiration();
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void refreshTokenHasLongerExpirationThanAccessToken() {
        String accessToken = provider.createAccessToken("account-012", "FAMILY");
        String refreshToken = provider.createRefreshToken("account-012", "FAMILY");

        Claims accessClaims = provider.validateToken(accessToken);
        Claims refreshClaims = provider.validateToken(refreshToken);

        assertNotNull(accessClaims);
        assertNotNull(refreshClaims);
        assertTrue(refreshClaims.getExpiration().after(accessClaims.getExpiration()));
    }
}
