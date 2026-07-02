package com.aiot.infra.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultCacheManager 单元测试。
 */
class DefaultCacheManagerTest {

    private DefaultCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new DefaultCacheManager();
    }

    @Test
    void getCache_shouldCreateCache() {
        Cache<String, String> cache = cacheManager.getCache("test");
        assertNotNull(cache);
        assertEquals("test", cache.getName());
    }

    @Test
    void getCache_shouldReturnSameCacheForSameName() {
        Cache<String, String> cache1 = cacheManager.getCache("test");
        Cache<String, String> cache2 = cacheManager.getCache("test");
        assertSame(cache1, cache2);
    }

    @Test
    void getCache_withConfig_shouldCreateCacheWithConfig() {
        CacheConfig config = CacheConfig.withTtl(Duration.ofMinutes(5));
        Cache<String, String> cache = cacheManager.getCache("test", config);
        assertNotNull(cache);
    }

    @Test
    void removeCache_shouldRemoveCache() {
        Cache<String, String> cache = cacheManager.getCache("test");
        cache.put("key1", "value1");

        cacheManager.removeCache("test");

        // 获取应该创建新缓存
        Cache<String, String> newCache = cacheManager.getCache("test");
        assertFalse(newCache.containsKey("key1"));
    }

    @Test
    void clearAll_shouldClearAllCaches() {
        Cache<String, String> cache1 = cacheManager.getCache("test1");
        Cache<String, String> cache2 = cacheManager.getCache("test2");

        cache1.put("key1", "value1");
        cache2.put("key2", "value2");

        cacheManager.clearAll();

        assertEquals(0, cache1.size());
        assertEquals(0, cache2.size());
    }
}
