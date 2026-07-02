package com.aiot.infra.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConcurrentHashMapCache 单元测试。
 */
class ConcurrentHashMapCacheTest {

    private ConcurrentHashMapCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new ConcurrentHashMapCache<>("test-cache");
    }

    @Test
    void putAndGet_shouldWork() {
        cache.put("key1", "value1");
        Optional<String> result = cache.get("key1");
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void get_shouldReturnEmptyForNonExistentKey() {
        Optional<String> result = cache.get("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    void getOrElse_shouldReturnValueFromSupplierWhenAbsent() {
        AtomicInteger counter = new AtomicInteger(0);
        String result = cache.getOrElse("key1", () -> "value" + counter.incrementAndGet());
        assertEquals("value1", result);
        assertEquals(1, counter.get());
    }

    @Test
    void getOrElse_shouldReturnCachedValueWhenPresent() {
        cache.put("key1", "cached");
        AtomicInteger counter = new AtomicInteger(0);
        String result = cache.getOrElse("key1", () -> "value" + counter.incrementAndGet());
        assertEquals("cached", result);
        assertEquals(0, counter.get());
    }

    @Test
    void evict_shouldRemoveEntry() {
        cache.put("key1", "value1");
        Optional<String> removed = cache.evict("key1");
        assertTrue(removed.isPresent());
        assertEquals("value1", removed.get());
        assertFalse(cache.containsKey("key1"));
    }

    @Test
    void evict_shouldReturnEmptyForNonExistentKey() {
        Optional<String> removed = cache.evict("nonexistent");
        assertFalse(removed.isPresent());
    }

    @Test
    void clear_shouldRemoveAllEntries() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void size_shouldReturnCorrectCount() {
        assertEquals(0, cache.size());
        cache.put("key1", "value1");
        assertEquals(1, cache.size());
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
    }

    @Test
    void containsKey_shouldReturnTrueForExistingKey() {
        cache.put("key1", "value1");
        assertTrue(cache.containsKey("key1"));
        assertFalse(cache.containsKey("key2"));
    }

    @Test
    void ttl_shouldExpireEntries() throws InterruptedException {
        // 创建带 TTL 的缓存（100ms）
        ConcurrentHashMapCache<String, String> ttlCache = new ConcurrentHashMapCache<>(
                "ttl-cache", CacheConfig.withTtl(Duration.ofMillis(100)));

        ttlCache.put("key1", "value1");
        assertTrue(ttlCache.get("key1").isPresent());

        // 等待过期
        Thread.sleep(150);
        assertFalse(ttlCache.get("key1").isPresent());
    }

    @Test
    void lru_shouldEvictEldestWhenFull() {
        // 创建容量为 2 的 LRU 缓存
        ConcurrentHashMapCache<String, String> lruCache = new ConcurrentHashMapCache<>(
                "lru-cache", CacheConfig.withLru(2));

        lruCache.put("key1", "value1");
        lruCache.put("key2", "value2");

        // 访问 key1 使其成为最近使用的
        lruCache.get("key1");

        // 添加 key3，应该淘汰 key2（最久未使用）
        lruCache.put("key3", "value3");

        assertEquals(2, lruCache.size());
        assertTrue(lruCache.containsKey("key1"));
        assertFalse(lruCache.containsKey("key2"));
        assertTrue(lruCache.containsKey("key3"));
    }

    @Test
    void getName_shouldReturnCacheName() {
        assertEquals("test-cache", cache.getName());
    }
}
