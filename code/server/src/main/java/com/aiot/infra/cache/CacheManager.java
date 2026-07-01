package com.aiot.infra.cache;

/**
 * 缓存管理器接口。
 * <p>
 * 提供缓存的创建、获取和管理能力。
 * 支持 TTL（Time-To-Live）和容量限制。
 * </p>
 */
public interface CacheManager {

    /**
     * 获取或创建缓存。
     *
     * @param name 缓存名称
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 缓存实例
     */
    <K, V> Cache<K, V> getCache(String name);

    /**
     * 获取或创建带配置的缓存。
     *
     * @param name   缓存名称
     * @param config 缓存配置
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存实例
     */
    <K, V> Cache<K, V> getCache(String name, CacheConfig config);

    /**
     * 移除缓存。
     *
     * @param name 缓存名称
     */
    void removeCache(String name);

    /**
     * 清除所有缓存。
     */
    void clearAll();
}
