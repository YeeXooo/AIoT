package com.aiot.infra.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token 提供者。
 * <p>
 * 负责 Access Token 和 Refresh Token 的签发、校验和解析。
 * 使用 HMAC-SHA256 签名算法，密钥由 KeyStoreKeyManager 提供。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §5.1
 * </p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final SecurityProperties properties;

    /** Access Token 有效期（秒） */
    private static final long ACCESS_TOKEN_VALIDITY_SEC = 3600; // 1 小时

    /** Refresh Token 有效期（秒） */
    private static final long REFRESH_TOKEN_VALIDITY_SEC = 86400; // 24 小时

    public JwtTokenProvider(KeyStoreKeyManager keyStoreManager, SecurityProperties properties) {
        this.properties = properties;
        byte[] keyBytes = keyStoreManager.getMasterKeyBytes();
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 Access Token。
     *
     * @param accountId 账户标识
     * @param role      账户角色 (FAMILY / MANAGER / RESCUE)
     */
    public String createAccessToken(String accountId, String role) {
        return createToken(accountId, role, ACCESS_TOKEN_VALIDITY_SEC, "access");
    }

    /**
     * 签发 Refresh Token。
     */
    public String createRefreshToken(String accountId, String role) {
        return createToken(accountId, role, REFRESH_TOKEN_VALIDITY_SEC, "refresh");
    }

    /**
     * 签发 Token Pair。
     */
    public TokenPair createTokenPair(String accountId, String role) {
        return new TokenPair(
                createAccessToken(accountId, role),
                createRefreshToken(accountId, role),
                "Bearer",
                ACCESS_TOKEN_VALIDITY_SEC,
                accountId,
                role
        );
    }

    /**
     * 校验 Token 并返回 Claims。
     *
     * @return 解析后的 Claims，Token 无效时返回 null
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取 accountId。
     */
    public String getAccountId(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从 Token 中提取 role。
     */
    public String getRole(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * 判断 Token 是否有效。
     */
    public boolean isValid(String token) {
        return validateToken(token) != null;
    }

    private String createToken(String accountId, String role, long validitySec, String type) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(validitySec);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        extraClaims.put("type", type);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(accountId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            String accountId,
            String role
    ) {}
}
