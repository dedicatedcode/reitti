package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.notification.NotificationChannelConfig;
import com.dedicatedcode.reitti.model.notification.NotificationSettings;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationChannelJdbcService;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.repository.NotificationSettingsJdbcService;
import com.dedicatedcode.reitti.repository.NotificationTypePreferenceJdbcService;
import com.dedicatedcode.reitti.service.notification.type.JobFailedNotificationType;
import com.dedicatedcode.reitti.service.notification.type.TestNotificationType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@IntegrationTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationJdbcService notificationJdbcService;

    @Autowired
    private NotificationSettingsJdbcService settingsJdbcService;

    @Autowired
    private NotificationTypePreferenceJdbcService preferenceJdbcService;

    @Autowired
    private NotificationChannelJdbcService channelJdbcService;

    @Autowired
    @Qualifier("notificationRestTemplate")
    private RestTemplate notificationRestTemplate;

    @Autowired
    private TestingService testingService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(notificationRestTemplate);
    }

    @AfterEach
    void tearDown() {
        mockServer.reset();
    }

    @Test
    void shouldStoreNotificationInInbox() {
        User user = testingService.randomUser();

        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        List<Notification> notifications = notificationJdbcService.findForUser(user.getId(), false, 10, 0);
        assertEquals(1, notifications.size());
        assertEquals(TestNotificationType.KEY, notifications.getFirst().typeKey());
        assertTrue(notifications.getFirst().unread());
        assertTrue(notifications.getFirst().title() != null && !notifications.getFirst().title().isBlank());
    }

    @Test
    void shouldRespectDisabledNotificationType() {
        User user = testingService.randomUser();
        preferenceJdbcService.save(user.getId(), TestNotificationType.KEY, false);

        assertFalse(notificationService.notify(user, TestNotificationType.KEY));

        assertEquals(0, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldRespectGlobalDisabledSetting() {
        User user = testingService.randomUser();
        NotificationSettings settings = settingsJdbcService.getOrCreate(user.getId());
        settingsJdbcService.save(settings.withEnabled(false));

        assertFalse(notificationService.notify(user, TestNotificationType.KEY));

        assertEquals(0, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldDedupeUnreadNotifications() {
        User user = testingService.randomUser();
        Map<String, Object> params = Map.of(
                "jobId", UUID.randomUUID().toString(),
                "friendlyName", "Test job");

        assertTrue(notificationService.notify(user, JobFailedNotificationType.KEY, params));
        assertFalse(notificationService.notify(user, JobFailedNotificationType.KEY, params));

        assertEquals(1, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldMarkNotificationAsRead() {
        User user = testingService.randomUser();
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        Notification notification = notificationJdbcService.findForUser(user.getId(), true, 1, 0).getFirst();
        assertTrue(notificationJdbcService.markRead(user.getId(), notification.id()));

        assertEquals(0, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldNotNotifyAnotherUser() {
        User user = testingService.randomUser();
        User other = testingService.randomUser();

        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        assertEquals(0, notificationJdbcService.countUnread(other.getId()));
    }

    @Test
    void shouldDeliverToEnabledExternalChannel() {
        User user = testingService.randomUser();
        channelJdbcService.save(new NotificationChannelConfig(
                null,
                user.getId(),
                "NTFY",
                true,
                Map.of("serverUrl", "https://ntfy.sh", "topic", "reitti-test"),
                null,
                null,
                null));

        mockServer.expect(requestTo("https://ntfy.sh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> mockServer.verify());
    }
}
