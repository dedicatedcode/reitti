package com.dedicatedcode.reitti.service.notification.producer;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.notification.NotificationRenderer;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.type.FailedLoginNotificationType;
import com.dedicatedcode.reitti.service.notification.type.JobCompletedNotificationType;
import com.dedicatedcode.reitti.service.notification.type.JobFailedNotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationProducers {
    private static final Logger log = LoggerFactory.getLogger(NotificationProducers.class);

    private final NotificationService notificationService;
    private final NotificationRenderer renderer;
    private final I18nService i18n;
    private final UserJdbcService userJdbcService;
    private final long completionMinDurationMinutes;

    public NotificationProducers(NotificationService notificationService,
                                 NotificationRenderer renderer,
                                 I18nService i18n,
                                 UserJdbcService userJdbcService,
                                 @Value("${reitti.notifications.jobs.completion-min-duration-minutes:5}") long completionMinDurationMinutes) {
        this.notificationService = notificationService;
        this.renderer = renderer;
        this.i18n = i18n;
        this.userJdbcService = userJdbcService;
        this.completionMinDurationMinutes = completionMinDurationMinutes;
    }

    public void jobFinished(UUID jobId, Long userId, JobType jobType, String friendlyName,
                            JobState state, Duration duration) {
        try {
            if (state == JobState.COMPLETED) {
                if (duration == null || duration.toMinutes() < completionMinDurationMinutes) {
                    return;
                }
                notifyJobOwner(jobId, userId, jobType, friendlyName, duration, JobCompletedNotificationType.KEY);
                return;
            }
            if (state == JobState.FAILED) {
                if (userId != null) {
                    notifyJobOwner(jobId, userId, jobType, friendlyName, duration, JobFailedNotificationType.KEY);
                } else {
                    notifyAdmins(jobId, jobType, friendlyName, duration);
                }
            }
        } catch (Exception e) {
            log.warn("Could not create job notification for job [{}]", jobId, e);
        }
    }

    public void failedLogin(String username, Instant windowStart) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        try {
            Optional<User> user = userJdbcService.findByUsername(username);
            if (user.isEmpty()) {
                log.debug("Ignoring lockout for unknown username [{}]", username);
                return;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("username", username);
            params.put("window", windowStart != null ? windowStart.toString() : "");
            notificationService.notify(user.get(), FailedLoginNotificationType.KEY, params);
        } catch (Exception e) {
            log.warn("Could not create failed login notification for [{}]", username, e);
        }
    }

    private void notifyJobOwner(UUID jobId, Long userId, JobType jobType, String friendlyName,
                                Duration duration, String typeKey) {
        if (userId == null) {
            return;
        }
        userJdbcService.findById(userId)
                .ifPresent(user -> notificationService.notify(user, typeKey,
                        params(jobId, jobType, friendlyName, duration, user)));
    }

    private void notifyAdmins(UUID jobId, JobType jobType, String friendlyName, Duration duration) {
        List<User> admins = userJdbcService.getAllUsers().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .toList();
        for (User admin : admins) {
            notificationService.notify(admin, JobFailedNotificationType.KEY,
                    params(jobId, jobType, friendlyName, duration, admin));
        }
    }

    private Map<String, Object> params(UUID jobId, JobType jobType, String friendlyName,
                                       Duration duration, User recipient) {
        Map<String, Object> params = new HashMap<>();
        params.put("jobId", jobId.toString());
        params.put("friendlyName", friendlyName != null ? friendlyName : "");
        params.put("jobType", jobType != null ? jobType.name() : "");
        params.put("duration", humanize(duration, recipient));
        return params;
    }

    private String humanize(Duration duration, User recipient) {
        if (duration == null) {
            return "";
        }
        try {
            Locale locale = renderer.localeFor(recipient);
            return i18n.humanizeDuration(duration, locale);
        } catch (Exception e) {
            return duration.toString();
        }
    }
}
