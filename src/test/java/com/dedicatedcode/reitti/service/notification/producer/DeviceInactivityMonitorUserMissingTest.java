package com.dedicatedcode.reitti.service.notification.producer;

import com.dedicatedcode.reitti.model.notification.DeviceInactivitySetting;
import com.dedicatedcode.reitti.repository.DeviceInactivityJdbcService;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceInactivityMonitorUserMissingTest {

    @Mock
    private DeviceInactivityJdbcService inactivityJdbcService;

    @Mock
    private DeviceJdbcService deviceJdbcService;

    @Mock
    private UserJdbcService userJdbcService;

    @Mock
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;

    @Mock
    private NotificationService notificationService;

    @Test
    void shouldSkipAndCleanUpSettingsWhenUserNoLongerExists() {
        DeviceInactivitySetting setting = new DeviceInactivitySetting(
                1L, 42L, 7L, 24, null, Instant.now(), Instant.now(), 1L);
        when(inactivityJdbcService.findAll()).thenReturn(List.of(setting));
        when(userJdbcService.findById(42L)).thenReturn(Optional.empty());

        DeviceInactivityMonitor monitor = new DeviceInactivityMonitor(
                inactivityJdbcService, deviceJdbcService, userJdbcService,
                sourceLocationPointJdbcService, notificationService);

        int delivered = assertDoesNotThrow(monitor::checkInactiveDevices);

        assertEquals(0, delivered);
        verify(inactivityJdbcService).deleteAllForUser(42L);
        verifyNoInteractions(deviceJdbcService, sourceLocationPointJdbcService, notificationService);
    }
}
