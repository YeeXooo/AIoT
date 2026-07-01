package com.aiot.infra.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认缓存管理器实现。
 * <p>
 * 基于 ConcurrentHashMap 管理多个缓存实例。
 * </p>
 */
@Component
public class DefaultCacheManager implements CacheManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultCacheManager.class);

    private final ConcurrentMap<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) caches.computeIfAbsent(name,
                k -> new ConcurrentHashMapCache<>(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, CacheConfig config) {
        return (Cache<K, V>) caches.computeIfAbsent(name,
                k -> new ConcurrentHashMapCache<>(name, config));
    }

    @Override
    public void removeCache(String name) {
        Cache<?, ?> removed = caches.remove(name);
        if (removed != null) {
            removed.clear();
            log.info("Cache removed: {}", name);
        }
    }

    @Override
    public void clearAll() {
        caches.values().forEach(Cache::clear);
        caches.clear();
        log.info("All caches cleared");
    }
}
