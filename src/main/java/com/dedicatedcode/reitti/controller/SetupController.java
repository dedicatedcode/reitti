package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SetupController {

    private final UserJdbcService userService;
    private final PasswordEncoder passwordEncoder;
    private final boolean localLoginDisabled;

    public SetupController(UserJdbcService userService,
                           PasswordEncoder passwordEncoder,
                           @Value("${reitti.security.local-login.disable:false}") boolean localLoginDisabled) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.localLoginDisabled = localLoginDisabled;
    }

    @GetMapping("/setup")
    public String setupPage(Model model) {
        User adminUser = getAdminUserWithEmptyPassword();
        if (adminUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", adminUser);
        return "setup";
    }

    @PostMapping("/setup")
    public String updateAdminPassword(@RequestParam String username, @RequestParam String password, @RequestParam String displayName, RedirectAttributes redirectAttributes) {
        User emptyPasswordAdmin = getAdminUserWithEmptyPassword();
        if (localLoginDisabled || emptyPasswordAdmin == null || !emptyPasswordAdmin.getUsername().equals(username)) {
            return "redirect:/login";
        }

        try {
            User updatedAdmin = new User(
                    emptyPasswordAdmin.getId(),
                    emptyPasswordAdmin.getUsername(),
                    passwordEncoder.encode(password),
                    displayName,
                    emptyPasswordAdmin.getProfileUrl(),
                    emptyPasswordAdmin.getExternalId(),
                    emptyPasswordAdmin.getRole(),
                    emptyPasswordAdmin.getUserType(),
                    emptyPasswordAdmin.getVersion()
            );

            userService.updateUser(updatedAdmin);
            redirectAttributes.addFlashAttribute("message", "Admin password set successfully!");
            return "redirect:/login";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to set admin password: " + e.getMessage());
            return "redirect:/setup";
        }
    }

    private User getAdminUserWithEmptyPassword() {
        return userService.getAllUsers().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .filter(admin -> {
                    String password = admin.getPassword();
                    return password == null || password.isEmpty();
                })
                .findFirst()
                .orElse(null);
    }
} 