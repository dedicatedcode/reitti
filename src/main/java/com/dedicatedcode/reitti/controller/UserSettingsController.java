package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/settings")
public class UserSettingsController {

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final MessageSource messageSource;

    public UserSettingsController(UserJdbcService userJdbcService, UserSettingsJdbcService userSettingsJdbcService, MessageSource messageSource) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.messageSource = messageSource;
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/users-content")
    public String getUsersContent(Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUsername);
        return "fragments/user-management :: users-content";
    }

    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        // Prevent self-deletion
        if (currentUser.getId().equals(userId)) {
            model.addAttribute("errorMessage", getMessage("message.error.user.self.delete"));
        } else {
            try {
                userJdbcService.deleteUser(userId);
                model.addAttribute("successMessage", getMessage("message.success.user.deleted"));
            } catch (Exception e) {
                model.addAttribute("errorMessage", getMessage("message.error.user.deletion", e.getMessage()));
            }
        }

        // Get updated user list and add to model
        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", authentication.getName());

        // Return the users-content fragment
        return "fragments/user-management :: users-content";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam String password,
                             @RequestParam String preferred_language,
                             Authentication authentication,
                             Model model) {
        try {
            if (StringUtils.hasText(username) && StringUtils.hasText(displayName) && StringUtils.hasText(password)) {
                userJdbcService.createUser(username, displayName, password);
                
                // Get the created user and create default settings with selected language
                User createdUser = userJdbcService.findByUsername(username).orElseThrow();
                UserSettings userSettings = new UserSettings(createdUser.getId(), false, preferred_language, List.of());
                userSettingsJdbcService.save(userSettings);
                
                model.addAttribute("successMessage", getMessage("message.success.user.created"));
            } else {
                model.addAttribute("errorMessage", getMessage("message.error.user.creation", "All fields must be filled"));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.creation", e.getMessage()));
        }

        // Get updated user list and add to model
        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", authentication.getName());

        // Return the users-content fragment
        return "fragments/user-management :: users-content";
    }

    @PostMapping("/users/update")
    public String updateUser(@RequestParam Long userId,
                             @RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam(required = false) String password,
                             @RequestParam String preferred_language,
                             Authentication authentication,
                             Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        boolean isCurrentUser = currentUser.getUsername().equals(currentUsername);

        try {
            userJdbcService.updateUser(userId, username, displayName, password);
            
            // Update user settings with selected language
            UserSettings existingSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            
            UserSettings updatedSettings = new UserSettings(userId, existingSettings.isPreferColoredMap(), preferred_language, existingSettings.getConnectedUserAccounts(), existingSettings.getVersion());
            userSettingsJdbcService.save(updatedSettings);
            
            model.addAttribute("successMessage", getMessage("message.success.user.updated"));

            // If the current user was updated, update the authentication
            if (isCurrentUser && !currentUsername.equals(username)) {
                // We need to re-authenticate with the new username
                model.addAttribute("requireRelogin", true);
                model.addAttribute("newUsername", username);
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.update", e.getMessage()));
        }

        // Get updated user list and add to model
        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", isCurrentUser ? username : currentUsername);

        // Return the users-content fragment
        return "fragments/user-management :: users-content";
    }

    @GetMapping("/user-form")
    public String getUserForm(@RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String username,
                              @RequestParam(required = false) String displayName,
                              Model model) {
        if (userId != null) {
            model.addAttribute("userId", userId);
            model.addAttribute("username", username);
            model.addAttribute("displayName", displayName);
            
            // Load user settings to get selected language
            UserSettings userSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
        } else {
            // Default language for new users
            model.addAttribute("selectedLanguage", "en");
        }
        return "fragments/user-management :: user-form";
    }
}
