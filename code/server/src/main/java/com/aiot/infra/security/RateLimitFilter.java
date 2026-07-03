package com.aiot.infra.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * API 限流过滤器。
 * <p>
 * 基于本地令牌桶算法，按账户维度限流。
 * 默认每秒 20 个请求，防止暴力破解和资源滥用。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §5.4
 * </p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 默认每秒允许的请求数 */
    private static final double DEFAULT_PERMITS_PER_SECOND = 20.0;

    /** 认证端点每秒允许的请求数（防止暴力破解） */
    private static final double AUTH_PERMITS_PER_SECOND = 5.0;

    /** accountId → 令牌桶 */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** 清理过期桶的间隔 */
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);

    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String accountId = extractAccountId(request);
        double permits = path.contains("/auth/") ? AUTH_PERMITS_PER_SECOND : DEFAULT_PERMITS_PER_SECOND;

        TokenBucket bucket = getBucket(accountId, permits);
        if (!bucket.tryAcquire()) {
            log.warn("API 限流触发: accountId={}, path={}", accountId, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"errorCode\":\"RATE_LIMIT\",\"message\":\"请求过于频繁，请稍后重试\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private TokenBucket getBucket(String key, double permitsPerSec) {
        periodicCleanup();
        return buckets.computeIfAbsent(key, k -> new TokenBucket(permitsPerSec));
    }

    private String extractAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7, Math.min(header.length(), 47)); // 截断 token 避免内存泄漏
        }
        return request.getRemoteAddr();
    }

    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lastCleanup = now;
            // 清除超过 30 分钟未使用的桶
            buckets.entrySet().removeIf(entry -> entry.getValue().isStale(CLEANUP_INTERVAL_MS));
        }
    }

    /**
     * 简化的令牌桶实现。
     */
    private static class TokenBucket {
        private final double permitsPerSecond;
        private double tokens;
        private long lastRefill;

        TokenBucket(double permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            this.tokens = permitsPerSecond; // 初始满桶
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized boolean isStale(long staleMillis) {
            long idleNanos = System.nanoTime() - lastRefill;
            return idleNanos > TimeUnit.MILLISECONDS.toNanos(staleMillis);
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefill) / 1_000_000_000.0;
            tokens = Math.min(permitsPerSecond, tokens + elapsed * permitsPerSecond);
            lastRefill = now;
        }
    }
}
