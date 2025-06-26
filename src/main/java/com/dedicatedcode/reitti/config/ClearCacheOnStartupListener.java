package com.dedicatedcode.reitti.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ClearCacheOnStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CacheManager cacheManager;

    public ClearCacheOnStartupListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        cacheManager.getCacheNames()
                .parallelStream()
                .forEach(n -> cacheManager.getCache(n).clear());
    }
}
