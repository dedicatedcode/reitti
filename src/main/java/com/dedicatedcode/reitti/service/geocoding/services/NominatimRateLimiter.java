package com.dedicatedcode.reitti.service.geocoding.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class NominatimRateLimiter {
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final long ONE_SECOND_MS = 1000;

    /**
     * Checks if Nominatim is available right now without waiting.
     */
    public boolean isAvailableNow() {
        return (System.currentTimeMillis() - lastRequestTime.get()) >= ONE_SECOND_MS;
    }

    /**
     * Attempts to acquire a slot. If a second has passed, updates the timestamp and returns true.
     * If not, returns false (caller must wait or skip).
     */
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        
        if (now - last >= ONE_SECOND_MS) {
            // Atomic Compare-And-Set to ensure thread safety without heavy synchronized blocks
            return lastRequestTime.compareAndSet(last, now);
        }
        return false;
    }

    /**
     * Forces the current thread to sleep until the 1-second window opens, then acquires it.
     */
    public void acquireBlockingly() {
        while (true) {
            long now = System.currentTimeMillis();
            long last = lastRequestTime.get();
            long timePassed = now - last;

            if (timePassed >= ONE_SECOND_MS) {
                if (lastRequestTime.compareAndSet(last, now)) {
                    return; // Successfully acquired!
                }
            } else {
                try {
                    // Sleep for the remaining fraction of the second
                    Thread.sleep(ONE_SECOND_MS - timePassed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting for Nominatim rate limit", e);
                }
            }
        }
    }
}