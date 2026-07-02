package com.aiot.infra.cache;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 缓存接口。
 * <p>
 * 提供基本的缓存操作：get/put/evict/clear。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface Cache<K, V> {

    /**
     * 获取缓存值。
     *
     * @param key 键
     * @return 缓存值，如果不存在或已过期则返回 Optional.empty()
     */
    Optional<V> get(K key);

    /**
     * 获取缓存值，如果不存在则使用 supplier 计算并缓存。
     *
     * @param key      键
     * @param supplier 值提供者
     * @return 缓存值
     */
    V getOrElse(K key, Supplier<V> supplier);

    /**
     * 存入缓存。
     *
     * @param key   键
     * @param value 值
     */
    void put(K key, V value);

    /**
     * 移除缓存项。
     *
     * @param key 键
     * @return 被移除的值，如果不存在则返回 Optional.empty()
     */
    Optional<V> evict(K key);

    /**
     * 清除所有缓存项。
     */
    void clear();

    /**
     * 获取缓存项数量。
     *
     * @return 缓存项数量
     */
    int size();

    /**
     * 检查缓存是否包含指定键。
     *
     * @param key 键
     * @return 如果包含则返回 true
     */
    boolean containsKey(K key);

    /**
     * 获取缓存名称。
     *
     * @return 缓存名称
     */
    String getName();
}
