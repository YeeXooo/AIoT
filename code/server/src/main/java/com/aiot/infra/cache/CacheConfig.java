package com.aiot.infra.cache;

import java.time.Duration;

/**
 * 缓存配置。
 *
 * @param ttl            缓存过期时间（Time-To-Live），null 表示永不过期
 * @param maxSize        最大缓存项数量，-1 表示无限制
 * @param evictionPolicy 淘汰策略
 */
public record CacheConfig(
        Duration ttl,
        int maxSize,
        EvictionPolicy evictionPolicy
) {
    /**
     * 默认配置：无 TTL，无容量限制。
     */
    public static final CacheConfig DEFAULT = new CacheConfig(null, -1, EvictionPolicy.NONE);

    /**
     * 创建带 TTL 的配置。
     *
     * @param ttl 过期时间
     * @return 配置
     */
    public static CacheConfig withTtl(Duration ttl) {
        return new CacheConfig(ttl, -1, EvictionPolicy.NONE);
    }

    /**
     * 创建带容量限制的 LRU 配置。
     *
     * @param maxSize 最大容量
     * @return 配置
     */
    public static CacheConfig withLru(int maxSize) {
        return new CacheConfig(null, maxSize, EvictionPolicy.LRU);
    }

    /**
     * 创建带 TTL 和容量限制的配置。
     *
     * @param ttl      过期时间
     * @param maxSize  最大容量
     * @return 配置
     */
    public static CacheConfig withTtlAndLru(Duration ttl, int maxSize) {
        return new CacheConfig(ttl, maxSize, EvictionPolicy.LRU);
    }

    /**
     * 淘汰策略枚举。
     */
    public enum EvictionPolicy {
        /**
         * 不淘汰。
         */
        NONE,
        /**
         * 最近最少使用。
         */
        LRU
    }
}
