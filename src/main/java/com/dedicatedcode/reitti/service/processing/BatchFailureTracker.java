package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BatchFailureTracker {

    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    public void recordFailure(User user, Instant batchStart) {
        failures.computeIfAbsent(key(user, batchStart), _ -> new AtomicInteger()).incrementAndGet();
    }

    public boolean exceedsLimit(User user, Instant batchStart) {
        AtomicInteger count = failures.get(key(user, batchStart));
        return count != null && count.get() >= MAX_CONSECUTIVE_FAILURES;
    }

    public void clear(User user, Instant batchStart) {
        failures.remove(key(user, batchStart));
    }

    private String key(User user, Instant batchStart) {
        return user.getId() + ":" + batchStart;
    }
}
