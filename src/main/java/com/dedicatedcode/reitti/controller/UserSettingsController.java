package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.ConnectedUserAccount;
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
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/settings")
public class UserSettingsController {

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public UserSettingsController(UserJdbcService userJdbcService, UserSettingsJdbcService userSettingsJdbcService, MessageSource messageSource, LocaleResolver localeResolver) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
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
                             @RequestParam(required = false) List<Long> connectedUserIds,
                             Authentication authentication,
                             Model model) {
        try {
            if (StringUtils.hasText(username) && StringUtils.hasText(displayName) && StringUtils.hasText(password)) {
                userJdbcService.createUser(username, displayName, password);
                
                // Get the created user and create default settings with selected language
                User createdUser = userJdbcService.findByUsername(username).orElseThrow();
                
                // Build connected user accounts list
                List<ConnectedUserAccount> connectedAccounts = buildConnectedUserAccounts(connectedUserIds);
                
                UserSettings userSettings = new UserSettings(createdUser.getId(), false, preferred_language, connectedAccounts);
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
                             @RequestParam(required = false) List<Long> connectedUserIds,
                             Authentication authentication,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        boolean isCurrentUser = currentUser.getUsername().equals(currentUsername);

        try {
            userJdbcService.updateUser(userId, username, displayName, password);
            
            // Update user settings with selected language and connected accounts
            UserSettings existingSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            
            // Build connected user accounts list
            List<ConnectedUserAccount> connectedAccounts = buildConnectedUserAccounts(connectedUserIds);
            
            UserSettings updatedSettings = new UserSettings(userId, existingSettings.isPreferColoredMap(), preferred_language, connectedAccounts, existingSettings.getVersion());
            userSettingsJdbcService.save(updatedSettings);
            
            // If the current user was updated, update the locale
            if (isCurrentUser) {
                Locale newLocale = Locale.forLanguageTag(preferred_language);
                localeResolver.setLocale(request, response, newLocale);
            }
            
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
            UserSettings userSettings = userSettingsJdbcService.findByUserId(userId).orElse(UserSettings.defaultSettings(userId));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
        } else {
            // Default values for new users
            model.addAttribute("selectedLanguage", "en");
        }
        
        return "fragments/user-management :: user-form";
    }

    @GetMapping("/connected-accounts-section")
    public String getConnectedAccountsSection(@RequestParam(required = false) Long userId, Model model) {
        // Get all users for the connected accounts selection, excluding the current user being edited
        List<User> allUsers = userJdbcService.getAllUsers();
        if (userId != null) {
            allUsers = allUsers.stream()
                    .filter(user -> !user.getId().equals(userId))
                    .toList();
        }
        model.addAttribute("availableUsers", allUsers);
        
        // Create a set of connected user IDs for easy lookup in the template
        if (userId != null) {
            UserSettings userSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            List<Long> connectedUserIds = userSettings.getConnectedUserAccounts().stream()
                    .map(ConnectedUserAccount::userId)
                    .toList();
            model.addAttribute("connectedUserIds", connectedUserIds);
        } else {
            model.addAttribute("connectedUserIds", List.of());
        }
        
        return "fragments/user-management :: connected-accounts-section";
    }

    private List<ConnectedUserAccount> buildConnectedUserAccounts(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }
        
        List<ConnectedUserAccount> accounts = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId != null) {
                // Generate a random color for each connected user
                String color = generateRandomColor();
                accounts.add(new ConnectedUserAccount(userId, color));
            }
        }
        return accounts;
    }
    
    private String generateRandomColor() {
        // Generate a random bright color
        java.util.Random random = new java.util.Random();
        int hue = random.nextInt(360);
        return String.format("hsl(%d, 70%%, 50%%)", hue);
    }
}
