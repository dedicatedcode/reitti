package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts consecutive failures of processing batches. A batch that failed {@link #MAX_CONSECUTIVE_FAILURES} times
 * in a row backs off: it stays unprocessed and is retried with an exponentially growing delay, while the pipeline
 * continues with the data after it.
 */
@Service
public class BatchFailureTracker {

    public static final int MAX_CONSECUTIVE_FAILURES = 3;
    static final Duration INITIAL_BACKOFF = Duration.ofMinutes(5);
    static final Duration MAX_BACKOFF = Duration.ofHours(6);

    private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public BatchFailureTracker() {
        this(Clock.systemUTC());
    }

    BatchFailureTracker(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records a failed attempt of the batch [batchStart, batchEnd].
     *
     * @return the time from which the batch may be retried, once it failed too often in a row
     */
    public Optional<Instant> recordFailure(User user, Instant batchStart, Instant batchEnd) {
        Failures updated = failures.compute(key(user, batchStart), (_, previous) -> {
            int count = previous == null ? 1 : previous.count() + 1;
            Instant retryAt = count < MAX_CONSECUTIVE_FAILURES ? null : clock.instant().plus(backoff(count));
            return new Failures(count, batchEnd, retryAt);
        });
        return Optional.ofNullable(updated.retryAt());
    }

    /**
     * @return the end of the batch starting at batchStart while that batch is backing off, empty if it may be processed
     */
    public Optional<Instant> backingOffUntilEndOf(User user, Instant batchStart) {
        Failures current = failures.get(key(user, batchStart));
        if (current == null || current.retryAt() == null || !clock.instant().isBefore(current.retryAt())) {
            return Optional.empty();
        }
        return Optional.of(current.batchEnd());
    }

    public void clear(User user, Instant batchStart) {
        failures.remove(key(user, batchStart));
    }

    private static Duration backoff(int failureCount) {
        int doublings = Math.min(failureCount - MAX_CONSECUTIVE_FAILURES, 16);
        Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << doublings);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private String key(User user, Instant batchStart) {
        return user.getId() + ":" + batchStart;
    }

    private record Failures(int count, Instant batchEnd, Instant retryAt) {
    }
}
