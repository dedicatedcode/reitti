package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserProcessingLock {
    private static final Logger log = LoggerFactory.getLogger(UserProcessingLock.class);

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void locked(User user, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(user.getId(), _ -> new ReentrantLock());
        long start = System.currentTimeMillis();
        lock.lock();
        long waited = System.currentTimeMillis() - start;
        if (waited > 1000) {
            log.info("Waited {}ms for processing lock of user [{}]", waited, user.getUsername());
        }
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
