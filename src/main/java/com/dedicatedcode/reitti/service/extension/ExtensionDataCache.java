package com.dedicatedcode.reitti.service.extension;

import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ExtensionDataCache {
    private static final int MAX_ENTRIES = 128;
    private final Map<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true);

    public synchronized Optional<Entry> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public synchronized void put(String key, byte[] body, String contentType, long ttlMs) {
        while (entries.size() >= MAX_ENTRIES) {
            Iterator<String> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        entries.put(key, new Entry(body, contentType, System.currentTimeMillis() + ttlMs));
    }

    public synchronized void invalidate(long registrationId) {
        String prefix = registrationId + "/";
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public record Entry(byte[] body, String contentType, long expiresAt) { }
}
