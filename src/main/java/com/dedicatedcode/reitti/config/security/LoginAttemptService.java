package com.dedicatedcode.reitti.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles password guessing on the login form: after too many failed attempts for a username within the window,
 * further attempts for that username are rejected until the window has passed. Keyed by username rather than IP,
 * because client IPs behind proxies are easy to vary.
 */
@Component
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${reitti.security.login.max-failures:10}") int maxFailures,
                               @Value("${reitti.security.login.lockout-minutes:15}") long lockoutMinutes) {
        this(maxFailures, Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        if (username == null) {
            return false;
        }
        Attempts current = attempts.get(key(username));
        return current != null && !current.expired(clock.instant(), window) && current.failures >= maxFailures;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String username = event.getAuthentication().getName();
        if (username == null || username.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED_USERNAMES) {
            attempts.values().removeIf(a -> a.expired(now, window));
        }
        Attempts updated = attempts.compute(key(username), (k, existing) ->
                existing == null || existing.expired(now, window) ? new Attempts(1, now) : new Attempts(existing.failures + 1, existing.firstFailure));
        if (updated.failures == maxFailures) {
            log.warn("Too many failed logins for user [{}], blocking further attempts for {} minutes", username, window.toMinutes());
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            attempts.remove(key(event.getAuthentication().getName()));
        }
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(int failures, Instant firstFailure) {
        boolean expired(Instant now, Duration window) {
            return firstFailure.plus(window).isBefore(now);
        }
    }
}
