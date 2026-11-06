package com.dedicatedcode.reitti.service.notification.producer;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.notification.DeviceInactivitySetting;
import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceInactivityJdbcService;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.repository.NotificationTypePreferenceJdbcService;
import com.dedicatedcode.reitti.service.notification.type.DeviceInactivityNotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@IntegrationTest
class DeviceInactivityMonitorTest {

    @Autowired
    private DeviceInactivityMonitor monitor;

    @Autowired
    private DeviceInactivityJdbcService inactivityJdbcService;

    @Autowired
    private NotificationJdbcService notificationJdbcService;

    @Autowired
    private NotificationTypePreferenceJdbcService preferenceJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        device = testingService.findDefaultDevice(user);
    }

    @Test
    void shouldNotifyOnlyOnceForOneInactivityPeriod() {
        inactivityJdbcService.save(user.getId(), device.id(), 1);

        monitor.checkInactiveDevices();
        monitor.checkInactiveDevices();

        assertEquals(1, notificationsForUser().size());
        assertNotNull(setting().lastNotifiedAt());
    }

    @Test
    void shouldNotifyAgainAfterDeviceBecomesActive() {
        inactivityJdbcService.save(user.getId(), device.id(), 1);

        monitor.checkInactiveDevices();
        assertEquals(1, notificationsForUser().size());

        Instant now = Instant.now();
        insertPoint(now);
        monitor.checkInactiveDevices();

        assertEquals(1, notificationsForUser().size());
        assertNull(setting().lastNotifiedAt());

        jdbcTemplate.update(
                "UPDATE raw_source_points SET timestamp = ? WHERE user_id = ? AND device_id = ?",
                Timestamp.from(now.minus(2, ChronoUnit.HOURS)), user.getId(), device.id());
        monitor.checkInactiveDevices();

        assertEquals(2, notificationsForUser().size());
        assertNotNull(setting().lastNotifiedAt());
    }

    @Test
    void shouldOnlyNotifyForConfiguredDevices() {
        Device secondDevice = testingService.createRandomDevice(user);
        inactivityJdbcService.save(user.getId(), secondDevice.id(), 1);

        monitor.checkInactiveDevices();

        List<Notification> notifications = notificationsForUser();
        assertEquals(1, notifications.size());
        assertEquals(secondDevice.name(), notifications.getFirst().params().get("deviceName"));
    }

    @Test
    void shouldNotNotifyWhenDeviceHasRecentData() {
        insertPoint(Instant.now().minus(30, ChronoUnit.MINUTES));
        inactivityJdbcService.save(user.getId(), device.id(), 1);

        monitor.checkInactiveDevices();

        assertEquals(0, notificationsForUser().size());
        assertNull(setting().lastNotifiedAt());
    }

    @Test
    void shouldNotNotifyWhenTypeIsDisabled() {
        preferenceJdbcService.save(user.getId(), DeviceInactivityNotificationType.KEY, false);
        inactivityJdbcService.save(user.getId(), device.id(), 1);

        monitor.checkInactiveDevices();

        assertEquals(0, notificationsForUser().size());
        assertNull(setting().lastNotifiedAt());
    }

    private List<Notification> notificationsForUser() {
        return notificationJdbcService.findForUser(user.getId(), false, 100, 0).stream()
                .filter(notification -> DeviceInactivityNotificationType.KEY.equals(notification.typeKey()))
                .toList();
    }

    private DeviceInactivitySetting setting() {
        return inactivityJdbcService.findByUserAndDevice(user.getId(), device.id()).orElseThrow();
    }

    private void insertPoint(Instant timestamp) {
        jdbcTemplate.update("""
                        INSERT INTO raw_source_points (user_id, device_id, timestamp, accuracy_meters, elevation_meters, geom, invalid, status)
                        VALUES (?, ?, ?, 10.0, NULL, ST_GeomFromText('POINT(9.99 53.55)', 4326), false, 0)
                        """,
                user.getId(), device.id(), Timestamp.from(timestamp));
    }
}
