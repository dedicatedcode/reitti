package com.dedicatedcode.reitti.service.notification.type;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link NewVersionNotificationType}.
 * <p>
 * The version arrives from {@link com.dedicatedcode.reitti.service.notification.producer.UpdateCheckService}
 * as the raw {@code tag_name} of the newest GitHub release (e.g. "v5.8.1"), stored under the
 * {@code "version"} key in the params map (see {@code UpdateCheckService#notifyAdmins}).
 */
class NewVersionNotificationTypeTest {

    private final NewVersionNotificationType type = new NewVersionNotificationType();

    @Test
    void shouldReturnActionUrlForNewestFoundVersion() {
        // Simulates the params map as built by UpdateCheckService.notifyAdmins()
        Map<String, Object> params = new HashMap<>();
        params.put("version", "v5.8.1");

        assertEquals("https://github.com/dedicatedcode/reitti/releases/tag/v5.8.1", type.actionUrl(params));
    }

    @Test
    void shouldReturnActionUrlForAnyVersion() {
        Map<String, Object> params = new HashMap<>();
        params.put("version", "v1.2.3");

        assertEquals("https://github.com/dedicatedcode/reitti/releases/tag/v1.2.3", type.actionUrl(params));
    }

    @Test
    void shouldFallbackToGeneralReleaseListWhenVersionIsMissing() {
        Map<String, Object> params = new HashMap<>();

        assertEquals("https://github.com/dedicatedcode/reitti/releases", type.actionUrl(params));
    }

    @Test
    void shouldFallbackToGeneralReleaseListWhenParamsIsNull() {
        assertEquals("https://github.com/dedicatedcode/reitti/releases", type.actionUrl(null));
    }

    @Test
    void shouldFallbackToGeneralReleaseListWhenVersionIsBlank() {
        Map<String, Object> params = new HashMap<>();
        params.put("version", "");

        assertEquals("https://github.com/dedicatedcode/reitti/releases", type.actionUrl(params));
    }

    @Test
    void shouldHandleVersionWithoutLeadingV() {
        Map<String, Object> params = new HashMap<>();
        params.put("version", "5.8.1");

        assertEquals("https://github.com/dedicatedcode/reitti/releases/tag/5.8.1", type.actionUrl(params));
    }
}
