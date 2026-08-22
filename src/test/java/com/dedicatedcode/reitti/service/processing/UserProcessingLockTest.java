package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProcessingLockTest {

    private final UserProcessingLock lock = new UserProcessingLock();

    @Test
    void ensuresMutualExclusionForSameUser() throws Exception {
        User user = testUser(1L);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicBoolean violation = new AtomicBoolean(false);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 50; j++) {
                    lock.locked(user, () -> {
                        if (concurrent.incrementAndGet() > 1) {
                            violation.set(true);
                        }
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            concurrent.decrementAndGet();
                        }
                    });
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

        assertFalse(violation.get());
    }

    @Test
    void differentUsersDoNotBlockEachOther() {
        User userOne = testUser(1L);
        User userTwo = testUser(2L);

        long start = System.currentTimeMillis();
        CompletableFuture<Void> holding = CompletableFuture.runAsync(() ->
                lock.locked(userOne, () -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        lock.locked(userTwo, () -> { });

        long elapsedWithOtherUser = System.currentTimeMillis() - start;
        assertTrue(elapsedWithOtherUser < 250, "Lock of another user should not block, but waited " + elapsedWithOtherUser + "ms");
        holding.join();
    }

    private User testUser(Long id) {
        return new User(id, "user" + id, null, "User " + id, null, null, Role.USER, UserType.NORMAL, 0L);
    }
}
