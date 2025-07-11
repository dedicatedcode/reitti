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
import java.util.Set;
import java.util.stream.Collectors;

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
                             @RequestParam(required = false) List<String> connectedUserColors,
                             Authentication authentication,
                             Model model) {
        try {
            if (StringUtils.hasText(username) && StringUtils.hasText(displayName) && StringUtils.hasText(password)) {
                userJdbcService.createUser(username, displayName, password);
                
                // Get the created user and create default settings with selected language
                User createdUser = userJdbcService.findByUsername(username).orElseThrow();
                
                // Build connected user accounts list
                List<ConnectedUserAccount> connectedAccounts = buildConnectedUserAccounts(connectedUserIds, connectedUserColors);
                
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
                             @RequestParam(required = false) List<String> connectedUserColors,
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
            List<ConnectedUserAccount> connectedAccounts = buildConnectedUserAccounts(connectedUserIds, connectedUserColors);
            
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
        
        // Get existing connected accounts for editing
        List<ConnectedUserAccount> connectedAccounts = List.of();
        if (userId != null) {
            UserSettings userSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            connectedAccounts = userSettings.getConnectedUserAccounts();
        }
        model.addAttribute("connectedAccounts", connectedAccounts);
        model.addAttribute("userId", userId);
        
        return "fragments/user-management :: connected-accounts-section";
    }

    @PostMapping("/connected-accounts/add")
    public String addConnectedAccount(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) List<Long> connectedUserIds,
                                      @RequestParam(required = false) List<String> connectedUserColors,
                                      Model model) {
        // Get all users for the connected accounts selection, excluding the current user being edited
        List<User> allUsers = userJdbcService.getAllUsers();
        if (userId != null) {
            allUsers = allUsers.stream()
                    .filter(user -> !user.getId().equals(userId))
                    .toList();
        }
        model.addAttribute("availableUsers", allUsers);
        
        // Build current connected accounts from existing form data
        List<ConnectedUserAccount> connectedAccounts = new ArrayList<>(buildConnectedUserAccounts(connectedUserIds, connectedUserColors));
        
        // Add a new empty account
        connectedAccounts.add(new ConnectedUserAccount(null, generateRandomColor()));
        
        model.addAttribute("connectedAccounts", connectedAccounts);
        model.addAttribute("userId", userId);
        
        return "fragments/user-management :: connected-accounts-section";
    }

    @PostMapping("/connected-accounts/remove")
    public String removeConnectedAccount(@RequestParam(required = false) Long userId,
                                         @RequestParam int removeIndex,
                                         @RequestParam(required = false) List<Long> connectedUserIds,
                                         @RequestParam(required = false) List<String> connectedUserColors,
                                         Model model) {
        // Get all users for the connected accounts selection, excluding the current user being edited
        List<User> allUsers = userJdbcService.getAllUsers();
        if (userId != null) {
            allUsers = allUsers.stream()
                    .filter(user -> !user.getId().equals(userId))
                    .toList();
        }
        model.addAttribute("availableUsers", allUsers);
        
        // Build current connected accounts from existing form data
        List<ConnectedUserAccount> connectedAccounts = new ArrayList<>(buildConnectedUserAccounts(connectedUserIds, connectedUserColors));
        
        // Remove the account at the specified index
        if (removeIndex >= 0 && removeIndex < connectedAccounts.size()) {
            connectedAccounts.remove(removeIndex);
        }
        
        model.addAttribute("connectedAccounts", connectedAccounts);
        model.addAttribute("userId", userId);
        
        return "fragments/user-management :: connected-accounts-section";
    }

    private List<ConnectedUserAccount> buildConnectedUserAccounts(List<Long> userIds, List<String> colors) {
        if (userIds == null) {
            return new ArrayList<>();
        }
        
        List<ConnectedUserAccount> accounts = new ArrayList<>();
        Set<Long> seenUserIds = new java.util.HashSet<>();
        
        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            String color = (colors != null && i < colors.size()) ? colors.get(i) : generateRandomColor();
            
            if (userId != null && !seenUserIds.contains(userId)) {
                accounts.add(new ConnectedUserAccount(userId, color));
                seenUserIds.add(userId);
            }
        }
        return accounts;
    }
    
    private String generateRandomColor() {
        // Generate a random bright color in hex RGB format
        java.util.Random random = new java.util.Random();
        
        // Generate bright colors by ensuring high saturation and medium-high lightness
        float hue = random.nextFloat(); // 0.0 to 1.0
        float saturation = 0.7f + random.nextFloat() * 0.3f; // 0.7 to 1.0
        float brightness = 0.5f + random.nextFloat() * 0.4f; // 0.5 to 0.9
        
        // Convert HSB to RGB without using java.awt
        int rgb = hsbToRgb(hue, saturation, brightness);
        
        // Convert to hex string
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
    
    private int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
            }
        }
        
        return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
    }
}
