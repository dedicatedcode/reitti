package com.dedicatedcode.reitti.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ClearCacheOnStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClearCacheOnStartupListener.class);

    private final CacheManager cacheManager;

    public ClearCacheOnStartupListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' was listed by CacheManager but returned null from getCache(). Skipping.", cacheName);
                continue;
            }
            cache.clear();
            log.debug("Cleared cache '{}'", cacheName);
        }
    }
}
