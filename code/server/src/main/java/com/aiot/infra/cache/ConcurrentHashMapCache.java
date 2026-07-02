package com.aiot.infra.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 基于 ConcurrentHashMap 的内存缓存实现。
 * <p>
 * 支持 TTL（Time-To-Live）和 LRU（Least Recently Used）淘汰策略。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class ConcurrentHashMapCache<K, V> implements Cache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentHashMapCache.class);

    private final String name;
    private final CacheConfig config;
    private final ConcurrentMap<K, CacheEntry<V>> store;
    private final AtomicLong accessCounter = new AtomicLong(0);

    public ConcurrentHashMapCache(String name) {
        this(name, CacheConfig.DEFAULT);
    }

    public ConcurrentHashMapCache(String name, CacheConfig config) {
        this.name = name;
        this.config = config;
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);

        if (entry == null) {
            return Optional.empty();
        }

        // 检查是否过期
        if (isExpired(entry)) {
            store.remove(key);
            log.debug("Cache entry expired: key={}", key);
            return Optional.empty();
        }

        // 更新访问顺序（用于 LRU）
        entry.lastAccessOrder = accessCounter.incrementAndGet();

        return Optional.of(entry.value);
    }

    @Override
    public V getOrElse(K key, Supplier<V> supplier) {
        return get(key).orElseGet(() -> {
            V value = supplier.get();
            put(key, value);
            return value;
        });
    }

    @Override
    public void put(K key, V value) {
        // 检查容量限制
        if (config.maxSize() > 0 && store.size() >= config.maxSize()) {
            evictEldest();
        }

        long order = accessCounter.incrementAndGet();
        Instant now = Instant.now();
        store.put(key, new CacheEntry<>(value, now, order));
        log.debug("Cache put: key={}, cache={}", key, name);
    }

    @Override
    public Optional<V> evict(K key) {
        CacheEntry<V> removed = store.remove(key);
        if (removed != null) {
            log.debug("Cache evict: key={}, cache={}", key, name);
            return Optional.of(removed.value);
        }
        return Optional.empty();
    }

    @Override
    public void clear() {
        store.clear();
        log.debug("Cache cleared: cache={}", name);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public boolean containsKey(K key) {
        return get(key).isPresent();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * 检查缓存项是否过期。
     */
    private boolean isExpired(CacheEntry<V> entry) {
        if (config.ttl() == null) {
            return false;
        }
        return entry.createdAt.plus(config.ttl()).isBefore(Instant.now());
    }

    /**
     * 淘汰最旧的缓存项（LRU 策略）。
     */
    private void evictEldest() {
        if (config.evictionPolicy() == CacheConfig.EvictionPolicy.NONE) {
            return;
        }

        K eldestKey = null;
        long eldestOrder = Long.MAX_VALUE;

        for (var entry : store.entrySet()) {
            if (entry.getValue().lastAccessOrder < eldestOrder) {
                eldestOrder = entry.getValue().lastAccessOrder;
                eldestKey = entry.getKey();
            }
        }

        if (eldestKey != null) {
            store.remove(eldestKey);
            log.debug("Cache evicted eldest: key={}, cache={}", eldestKey, name);
        }
    }

    /**
     * 缓存项。
     */
    private static class CacheEntry<V> {
        final V value;
        final Instant createdAt;
        volatile long lastAccessOrder;

        CacheEntry(V value, Instant createdAt, long lastAccessOrder) {
            this.value = value;
            this.createdAt = createdAt;
            this.lastAccessOrder = lastAccessOrder;
        }
    }
}
