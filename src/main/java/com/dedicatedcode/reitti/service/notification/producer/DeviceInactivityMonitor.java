package com.dedicatedcode.reitti.service.notification.producer;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.notification.DeviceInactivitySetting;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceInactivityJdbcService;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.type.DeviceInactivityNotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Scans all configured device inactivity settings and notifies the owner once when a device
 * crosses the configured threshold without receiving any location data.
 * <p>
 * The {@code lastNotifiedAt} state on the setting implements the rising-flank behaviour: it is
 * cleared when the device sends data again, so the next inactivity period notifies once more.
 */
@Component
public class DeviceInactivityMonitor {
    private static final Logger log = LoggerFactory.getLogger(DeviceInactivityMonitor.class);

    private final DeviceInactivityJdbcService inactivityJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final UserJdbcService userJdbcService;
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final NotificationService notificationService;

    public DeviceInactivityMonitor(DeviceInactivityJdbcService inactivityJdbcService,
                                   DeviceJdbcService deviceJdbcService,
                                   UserJdbcService userJdbcService,
                                   SourceLocationPointJdbcService sourceLocationPointJdbcService,
                                   NotificationService notificationService) {
        this.inactivityJdbcService = inactivityJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.userJdbcService = userJdbcService;
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${reitti.notifications.device-inactivity.cron:0 */15 * * * *}")
    public void scheduledCheck() {
        checkInactiveDevices();
    }

    /**
     * @return the number of notifications that were delivered.
     */
    public int checkInactiveDevices() {
        Instant now = Instant.now();
        int delivered = 0;
        for (DeviceInactivitySetting setting : inactivityJdbcService.findAll()) {
            try {
                if (checkSetting(setting, now)) {
                    delivered++;
                }
            } catch (Exception e) {
                log.warn("Could not check device inactivity for user [{}] and device [{}]",
                        setting.userId(), setting.deviceId(), e);
            }
        }
        return delivered;
    }

    private boolean checkSetting(DeviceInactivitySetting setting, Instant now) {
        Optional<User> user = userJdbcService.findById(setting.userId());
        if (user.isEmpty()) {
            inactivityJdbcService.deleteAllForUser(setting.userId());
            log.debug("Removed device inactivity settings for missing user [{}]", setting.userId());
            return false;
        }
        Optional<Device> device = deviceJdbcService.find(user.get(), setting.deviceId());
        if (device.isEmpty()) {
            return false;
        }

        Optional<Instant> latestData = sourceLocationPointJdbcService.findLatestTimestamp(user.get(), device.get());
        Instant threshold = now.minus(Duration.ofHours(setting.hours()));
        boolean inactive = latestData.map(timestamp -> !timestamp.isAfter(threshold)).orElse(true);

        if (!inactive) {
            if (setting.lastNotifiedAt() != null) {
                inactivityJdbcService.resetNotification(setting.id());
                log.debug("Device [{}] of user [{}] is active again, resetting inactivity notification state",
                        device.get().id(), user.get().getId());
            }
            return false;
        }

        boolean newInactivityPeriod = setting.lastNotifiedAt() == null
                || latestData.map(timestamp -> timestamp.isAfter(setting.lastNotifiedAt())).orElse(false);
        if (!newInactivityPeriod) {
            return false;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("deviceName", device.get().name());
        params.put("deviceId", device.get().id());
        params.put("hours", setting.hours());

        if (notificationService.notify(user.get(), DeviceInactivityNotificationType.KEY, params)) {
            inactivityJdbcService.markNotified(setting.id(), now);
            log.info("Notified user [{}] that device [{}] exceeded its inactivity threshold ({}h)",
                    user.get().getId(), device.get().id(), setting.hours());
            return true;
        }
        return false;
    }
}
