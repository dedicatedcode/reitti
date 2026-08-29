package com.dedicatedcode.reitti.service.jobs;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromotionInflightGuard {

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(String partitionKey) {
        return this.inFlight.add(partitionKey);
    }

    public void release(String partitionKey) {
        this.inFlight.remove(partitionKey);
    }
}
