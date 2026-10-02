package com.dedicatedcode.reitti.config.security;

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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@IntegrationTest
class LoginThrottleTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void passwordGuessingIsBlockedPerUsername() throws Exception {
        User victim = createUser();
        User bystander = createUser();

        for (int i = 0; i < 10; i++) {
            login(victim.getUsername(), "wrong-" + i).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        }
        // the right password no longer helps while the username is blocked, and the answer looks the same
        login(victim.getUsername(), PASSWORD).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        // other accounts are unaffected
        login(bystander.getUsername(), PASSWORD).andExpect(authenticated());
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        User user = createUser();
        for (int i = 0; i < 9; i++) {
            login(user.getUsername(), "wrong-" + i);
        }
        login(user.getUsername(), PASSWORD).andExpect(authenticated());
        for (int i = 0; i < 9; i++) {
            login(user.getUsername(), "wrong-" + i);
        }
        login(user.getUsername(), PASSWORD).andExpect(authenticated());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/login").param("username", username).param("password", password));
    }

    private User createUser() {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userService.createNewUser(username, "User", PASSWORD, Role.USER, UnitSystem.METRIC,
                Language.EN, null, null, null, TimeDisplayMode.DEFAULT, TimeMode.TWENTY_FOUR_HOUR, "#e2e2e2");
    }
}
