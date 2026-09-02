package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private User createTestUser() {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return this.userService.createNewUser(username, "Index Test User", "password", Role.USER, UnitSystem.METRIC, Language.EN, null, null, null, TimeDisplayMode.DEFAULT, TimeMode.TWENTY_FOUR_HOUR, "#e2e2e2");
    }

    @Test
    void getIndex_AsAuthenticatedUser_ShouldRenderDatePickerContract() throws Exception {
        User testUser = createTestUser();

        mockMvc.perform(get("/").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("createDatePicker")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("window.datePicker")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("date-jump.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("datetime-picker.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("date-jump-hint")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("window.horizontalDatePicker"))));
    }
}
