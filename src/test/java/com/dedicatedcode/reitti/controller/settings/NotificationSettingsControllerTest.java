package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.notification.NotificationChannelConfig;
import com.dedicatedcode.reitti.model.notification.NotificationSettings;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationChannelJdbcService;
import com.dedicatedcode.reitti.repository.NotificationSettingsJdbcService;
import com.dedicatedcode.reitti.repository.NotificationTypePreferenceJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class NotificationSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private NotificationSettingsJdbcService settingsJdbcService;

    @Autowired
    private NotificationTypePreferenceJdbcService preferenceJdbcService;

    @Autowired
    private NotificationChannelJdbcService channelJdbcService;

    private User user;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
    }

    @Test
    void shouldRenderAllNotificationSettingsSections() throws Exception {
        MvcResult result = mockMvc.perform(get("/settings/notifications").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications")))
                .andExpect(content().string(containsString("Notification Settings")))
                .andExpect(content().string(containsString("Notification Types")))
                .andExpect(content().string(containsString("Channels")))
                .andExpect(content().string(containsString("ntfy")))
                .andExpect(content().string(containsString("Apprise")))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<NotificationSettingsController.ChannelView> channels =
                (List<NotificationSettingsController.ChannelView>) result.getModelAndView().getModel().get("notificationChannels");

        assertFalse(channels.isEmpty());
        assertEquals("IN_APP", channels.getFirst().channel().key());
    }

    @Test
    void shouldSaveGlobalNotificationSettings() throws Exception {
        mockMvc.perform(post("/settings/notifications/settings")
                        .with(user(user))
                        .with(csrf())
                        .param("enabled", "true")
                        .param("showBadge", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/notifications"));

        NotificationSettings settings = settingsJdbcService.getOrCreate(user.getId());
        assertTrue(settings.enabled());
        assertFalse(settings.showBadge());
    }

    @Test
    void shouldSaveTypePreferences() throws Exception {
        mockMvc.perform(post("/settings/notifications/types")
                        .with(user(user))
                        .with(csrf())
                        .param("typeKeys", "JOB_FAILED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/notifications"));

        assertTrue(preferenceJdbcService.isEnabled(user.getId(), "JOB_FAILED", false));
        assertFalse(preferenceJdbcService.isEnabled(user.getId(), "JOB_COMPLETED", true));
    }

    @Test
    void shouldSaveNtfyChannelConfiguration() throws Exception {
        mockMvc.perform(post("/settings/notifications/channels/NTFY")
                        .with(user(user))
                        .with(csrf())
                        .param("enabled", "true")
                        .param("cfg.serverUrl", "https://ntfy.sh")
                        .param("cfg.topic", "reitti-test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/notifications"));

        NotificationChannelConfig config = channelJdbcService
                .findByUserAndKey(user.getId(), "NTFY")
                .orElseThrow();
        assertTrue(config.enabled());
        assertEquals("https://ntfy.sh", config.config().get("serverUrl"));
        assertEquals("reitti-test", config.config().get("topic"));
    }

    @Test
    void shouldRejectInvalidNtfyConfiguration() throws Exception {
        mockMvc.perform(post("/settings/notifications/channels/NTFY")
                        .with(user(user))
                        .with(csrf())
                        .param("enabled", "true")
                        .param("cfg.serverUrl", "not-a-url")
                        .param("cfg.topic", "reitti-test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/notifications"))
                .andExpect(flash().attribute("errorMessage", "notifications.channel.ntfy.server.url.invalid"));

        assertTrue(channelJdbcService.findByUserAndKey(user.getId(), "NTFY").isEmpty());
    }
}
