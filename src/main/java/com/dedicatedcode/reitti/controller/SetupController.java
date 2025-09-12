package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class SetupController {

    private final UserJdbcService userService;
    private final PasswordEncoder passwordEncoder;

    public SetupController(UserJdbcService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/setup")
    public String setupPage(Model model) {
        User adminUser = getAdminUserWithEmptyPassword();
        if (adminUser == null) {
            return "redirect:/login"; // Setup already complete                                                                                                                                                                                                                     
        }

        model.addAttribute("user", adminUser);
        return "setup";
    }

    @PostMapping("/setup")
    public String updateAdminPassword(@RequestParam String username, @RequestParam String password, @RequestParam String displayName, RedirectAttributes redirectAttributes) {
        Optional<User> adminUser = userService.findByUsername(username);
        if (adminUser.isEmpty()) {
            return "redirect:/login";
        }

        if (!adminUser.get().getUsername().equals(username)) {
            throw new IllegalArgumentException("Wrong username or password");
        }
        try {
            User updatedAdmin = new User(
                    adminUser.get().getId(),
                    adminUser.get().getUsername(),
                    passwordEncoder.encode(password),
                    displayName,
                    adminUser.get().getProfileUrl(),
                    adminUser.get().getExternalId(),
                    Role.ADMIN,
                    adminUser.get().getVersion()
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