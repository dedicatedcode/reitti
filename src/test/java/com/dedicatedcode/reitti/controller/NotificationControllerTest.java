package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.type.FailedLoginNotificationType;
import com.dedicatedcode.reitti.service.notification.type.NewVersionNotificationType;
import com.dedicatedcode.reitti.service.notification.type.TestNotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationJdbcService notificationJdbcService;

    private User user;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
    }

    @Test
    void shouldRenderNotificationHistoryPageWithSettingsLink() throws Exception {
        mockMvc.perform(get("/notifications").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications")))
                .andExpect(content().string(containsString("Notification settings")))
                .andExpect(content().string(containsString("/settings/notifications")))
                .andExpect(content().string(containsString("settings-nav")))
                .andExpect(content().string(containsString("/settings/job-status")));
    }

    @Test
    void shouldRenderReadAndUnreadNotifications() throws Exception {
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        Notification first = notificationJdbcService.findForUser(user.getId(), true, 2, 0).getFirst();
        notificationJdbcService.markRead(user.getId(), first.id());

        mockMvc.perform(get("/notifications").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("notifications", hasSize(2)))
                .andExpect(content().string(containsString("severity-info")))
                .andExpect(content().string(containsString(">Info<")));
    }

    @Test
    void shouldRenderPendingDialogForNewVersion() throws Exception {
        User admin = testingService.randomAdmin();
        assertTrue(notificationService.notify(admin, NewVersionNotificationType.KEY, Map.of("version", "v9.9.9")));
        Notification notification = notificationJdbcService.findForUser(admin.getId(), true, 1, 0).getFirst();

        mockMvc.perform(get("/notifications/pending").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reitti-dialog")))
                .andExpect(content().string(containsString("Reitti <strong>v9.9.9</strong>")))
                .andExpect(content().string(containsString("/notifications/" + notification.id() + "/dismiss")));
    }

    @Test
    void shouldNotRenderPendingForInboxOnlyNotification() throws Exception {
        assertTrue(notificationService.notify(user, FailedLoginNotificationType.KEY));

        mockMvc.perform(get("/notifications/pending").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("reitti-dialog"))))
                .andExpect(content().string(not(containsString("notification-toast"))));
    }

    @Test
    void shouldDismissPendingNotification() throws Exception {
        User admin = testingService.randomAdmin();
        assertTrue(notificationService.notify(admin, NewVersionNotificationType.KEY, Map.of("version", "v9.9.9")));
        Notification notification = notificationJdbcService.findForUser(admin.getId(), true, 1, 0).getFirst();

        mockMvc.perform(post("/notifications/" + notification.id() + "/dismiss")
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("reitti-dialog"))));

        assertEquals(0, notificationJdbcService.countUnread(admin.getId()));
    }

    @Test
    void shouldOpenPendingNotificationAndMarkRead() throws Exception {
        User admin = testingService.randomAdmin();
        assertTrue(notificationService.notify(admin, NewVersionNotificationType.KEY, Map.of("version", "v9.9.9")));
        Notification notification = notificationJdbcService.findForUser(admin.getId(), true, 1, 0).getFirst();

        mockMvc.perform(post("/notifications/" + notification.id() + "/open")
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://github.com/dedicatedcode/reitti/releases/tag/v9.9.9"));

        assertEquals(0, notificationJdbcService.countUnread(admin.getId()));
    }

    @Test
    void shouldRenderHtmlInInboxForNewVersion() throws Exception {
        User admin = testingService.randomAdmin();
        assertTrue(notificationService.notify(admin, NewVersionNotificationType.KEY, Map.of("version", "v9.9.9")));

        mockMvc.perform(get("/notifications").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reitti <strong>v9.9.9</strong> is available")));
    }

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));
        Notification notification = notificationJdbcService.findForUser(user.getId(), true, 1, 0).getFirst();

        mockMvc.perform(post("/notifications/" + notification.id() + "/read")
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(0, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldMarkAllNotificationsAsRead() throws Exception {
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        mockMvc.perform(post("/notifications/read-all")
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(0, notificationJdbcService.countUnread(user.getId()));
    }

    @Test
    void shouldDeleteNotification() throws Exception {
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));
        Notification notification = notificationJdbcService.findForUser(user.getId(), true, 1, 0).getFirst();

        mockMvc.perform(post("/notifications/" + notification.id() + "/delete")
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(0, notificationJdbcService.findForUser(user.getId(), false, 10, 0).size());
    }

    @Test
    void shouldClearAllNotifications() throws Exception {
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));
        assertTrue(notificationService.notify(user, TestNotificationType.KEY));

        mockMvc.perform(post("/notifications/clear-all")
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(0, notificationJdbcService.findForUser(user.getId(), false, 10, 0).size());
    }

    @Test
    void shouldNotMarkAnotherUsersNotificationAsRead() throws Exception {
        User other = testingService.randomUser();
        assertTrue(notificationService.notify(other, TestNotificationType.KEY));
        Notification notification = notificationJdbcService.findForUser(other.getId(), true, 1, 0).getFirst();

        mockMvc.perform(post("/notifications/" + notification.id() + "/read")
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertTrue(notificationJdbcService.findForUser(other.getId(), true, 1, 0).getFirst().unread());
    }
}
