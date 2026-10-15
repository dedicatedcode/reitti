package com.dedicatedcode.reitti.service.statistics;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Tracks a per-user data version. Cached statistics include the version in their key, so bumping it invalidates
 * every cached report of the user at once.
 */
@Service
public class StatisticsDataVersionService {

    static final String VERSION_CACHE = "statistics-version";

    private final CacheManager cacheManager;

    public StatisticsDataVersionService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public long currentVersion(Long userId) {
        Cache cache = cache();
        Long version = cache.get(userId, Long.class);
        if (version == null) {
            version = System.currentTimeMillis();
            cache.put(userId, version);
        }
        return version;
    }

    public void bump(Long userId) {
        if (userId == null) {
            return;
        }
        Cache cache = cache();
        Long current = cache.get(userId, Long.class);
        long next = Math.max(System.currentTimeMillis(), current != null ? current + 1 : 0);
        cache.put(userId, next);
    }

    private Cache cache() {
        return Objects.requireNonNull(cacheManager.getCache(VERSION_CACHE), "Missing cache " + VERSION_CACHE);
    }
}
