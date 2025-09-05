package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.dedicatedcode.reitti.model.Role.ADMIN;
import static com.dedicatedcode.reitti.model.Role.USER;

@Controller
@RequestMapping("/settings")
public class UserSettingsController {

    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;
    private final AvatarService avatarService;
    private final PasswordEncoder passwordEncoder;

    // Avatar constraints
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    };
    private static final List<String> DEFAULT_AVATARS = Arrays.asList(
        "avatar_man.jpg", "avatar_woman.jpg", "avatar_boy.jpg", "avatar_girl.jpg"
    );

    public UserSettingsController(UserJdbcService userJdbcService,
                                  UserSettingsJdbcService userSettingsJdbcService,
                                  MessageSource messageSource,
                                  LocaleResolver localeResolver,
                                  AvatarService avatarService,
                                  PasswordEncoder passwordEncoder) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.avatarService = avatarService;
        this.passwordEncoder = passwordEncoder;
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @GetMapping("/users-content")
    public String getUsersContent(Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));
        return getUserContent(model, currentUser);
    }

    @GetMapping("/user-management")
    public String getUserManagementPage(Authentication authentication, Model model) {
        model.addAttribute("activeSection", "user-management");
        getUsersContent(authentication, model);
        return "settings";
    }

    private String getUserContent(Model model, User currentUser) {
        if (ADMIN != currentUser.getRole()) {
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("displayName", currentUser.getDisplayName());
            model.addAttribute("selectedRole", currentUser.getRole());
            UserSettings userSettings = userSettingsJdbcService.findByUserId(currentUser.getId()).orElse(UserSettings.defaultSettings(currentUser.getId()));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
            model.addAttribute("selectedUnitSystem", userSettings.getUnitSystem().name());
            model.addAttribute("preferColoredMap", userSettings.isPreferColoredMap());
            model.addAttribute("homeLatitude", userSettings.getHomeLatitude());
            model.addAttribute("homeLongitude", userSettings.getHomeLongitude());

            // Add available unit systems to model
            model.addAttribute("unitSystems", UnitSystem.values());

            // Check if user has avatar
            boolean hasAvatar = this.avatarService.getInfo(currentUser.getId()).isPresent();
            model.addAttribute("hasAvatar", hasAvatar);

            // Add default avatars to model
            model.addAttribute("defaultAvatars", DEFAULT_AVATARS);

            // Add admin status to model
            model.addAttribute("isAdmin", false);

            return "fragments/user-management :: user-form-page";
        }

        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("isAdmin", true);
        return "fragments/user-management :: users-list";
    }

    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, Authentication authentication, Model model) {
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        // Only admins can delete users
        if (ADMIN != currentUser.getRole()) {
            model.addAttribute("errorMessage", getMessage("message.error.access.denied"));
            return getUserContent(model, currentUser);
        }

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
        model.addAttribute("isAdmin", true);

        // Return the users-list fragment
        return "fragments/user-management :: users-list";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam String password,
                             @RequestParam(defaultValue = "USER") Role role,
                             @RequestParam String preferred_language,
                             @RequestParam(defaultValue = "METRIC") String unit_system,
                             @RequestParam(defaultValue = "false") boolean preferColoredMap,
                             @RequestParam(required = false) Double homeLatitude,
                             @RequestParam(required = false) Double homeLongitude,
                             @RequestParam(required = false) MultipartFile avatar,
                             @RequestParam(required = false) String defaultAvatar,
                             Authentication authentication,
                             Model model) {
        
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        // Only admins can create users
        if (ADMIN != currentUser.getRole()) {
            model.addAttribute("errorMessage", getMessage("message.error.access.denied"));
            return getUserContent(model, currentUser);
        }
        try {
            if (StringUtils.hasText(username) && StringUtils.hasText(displayName) && StringUtils.hasText(password)) {
                User createdUser = userJdbcService.createUser(username, displayName, password);
                
                // Update the user's role if different from default
                if (USER != role) {
                    createdUser = createdUser.withRole(role);
                    createdUser = userJdbcService.updateUser(createdUser);
                }
                
                UnitSystem unitSystem = UnitSystem.valueOf(unit_system);
                UserSettings userSettings = new UserSettings(createdUser.getId(), preferColoredMap, preferred_language, unitSystem, homeLatitude, homeLongitude, null);
                userSettingsJdbcService.save(userSettings);
                
                // Handle avatar - prioritize custom upload over default
                if (avatar != null && !avatar.isEmpty()) {
                    handleAvatarUpload(avatar, createdUser.getId(), model);
                } else if (StringUtils.hasText(defaultAvatar)) {
                    handleDefaultAvatarSelection(defaultAvatar, createdUser.getId(), model);
                }
                
                model.addAttribute("successMessage", getMessage("message.success.user.created"));
            } else {
                model.addAttribute("errorMessage", getMessage("message.error.user.creation", "All fields must be filled"));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.user.creation", e.getMessage()));
        }

        List<User> users = userJdbcService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentUsername", authentication.getName());
        model.addAttribute("isAdmin", true);

        // Return the users-list fragment
        return "fragments/user-management :: users-list";
    }

    @PostMapping("/users/update")
    public String updateUser(@RequestParam Long userId,
                             @RequestParam String username,
                             @RequestParam String displayName,
                             @RequestParam(required = false) String password,
                             @RequestParam(defaultValue = "USER") Role role,
                             @RequestParam String preferred_language,
                             @RequestParam(defaultValue = "METRIC") String unit_system,
                             @RequestParam(defaultValue = "false") boolean preferColoredMap,
                             @RequestParam(required = false) Double homeLatitude,
                             @RequestParam(required = false) Double homeLongitude,
                             @RequestParam(required = false) MultipartFile avatar,
                             @RequestParam(required = false) String defaultAvatar,
                             @RequestParam(required = false) String removeAvatar,
                             Authentication authentication,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Model model) {
        String currentUsername = authentication.getName();
        User authenticatedUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));
        User userToUpdate = userJdbcService.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        boolean isCurrentUser = userToUpdate.getUsername().equals(currentUsername);

        // Only admins can edit other users, users can only edit themselves
        if (!isCurrentUser && ADMIN != authenticatedUser.getRole()) {
            model.addAttribute("errorMessage", getMessage("message.error.access.denied"));
            return getUserContent(model, authenticatedUser);
        }

        try {
            User existingUser = userJdbcService.findById(userId).orElseThrow();
            
            String encodedPassword = existingUser.getPassword();
            // Only update password if provided
            if (password != null && !password.trim().isEmpty()) {
                encodedPassword = passwordEncoder.encode(password);
            }
            
            User updatedUser = new User(existingUser.getId(), username, encodedPassword, displayName, role, existingUser.getVersion());
            userJdbcService.updateUser(updatedUser);
            
            UserSettings existingSettings = userSettingsJdbcService.findByUserId(userId)
                .orElse(UserSettings.defaultSettings(userId));
            
            UnitSystem unitSystem = UnitSystem.valueOf(unit_system);
            UserSettings updatedSettings = new UserSettings(userId, preferColoredMap, preferred_language, unitSystem, homeLatitude, homeLongitude, existingSettings.getLatestData(), existingSettings.getVersion());
            userSettingsJdbcService.save(updatedSettings);
            
            // Handle avatar operations
            if ("true".equals(removeAvatar)) {
                avatarService.deleteAvatar(userId);
            } else if (avatar != null && !avatar.isEmpty()) {
                handleAvatarUpload(avatar, userId, model);
            } else if (StringUtils.hasText(defaultAvatar)) {
                handleDefaultAvatarSelection(defaultAvatar, userId, model);
            }
            
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

        // If admin, return to user list; if regular user, stay on their form
        if (ADMIN == authenticatedUser.getRole()) {
            List<User> users = userJdbcService.getAllUsers();
            model.addAttribute("users", users);
            model.addAttribute("currentUsername", isCurrentUser ? username : currentUsername);
            model.addAttribute("isAdmin", true);
            return "fragments/user-management :: users-list";
        } else {
            // For regular users, return their updated form
            return getUserContent(model, userToUpdate);
        }
    }

    @GetMapping("/user-form")
    public String getUserForm(@RequestParam(required = false) Long userId,
                              @RequestParam(required = false) String username,
                              @RequestParam(required = false) String displayName,
                              @RequestParam(required = false) String role,
                              Authentication authentication,
                              Model model) {
        
        String currentUsername = authentication.getName();
        User currentUser = userJdbcService.findByUsername(currentUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + currentUsername));

        // Only admins can access user forms for other users or create new users
        if (userId != null && !userId.equals(currentUser.getId()) && ADMIN != currentUser.getRole()) {
            model.addAttribute("errorMessage", getMessage("message.error.access.denied"));
            return getUserContent(model, currentUser);
        }
        
        if (userId == null && ADMIN != currentUser.getRole()) {
            model.addAttribute("errorMessage", getMessage("message.error.access.denied"));
            return getUserContent(model, currentUser);
        }
        if (userId != null) {
            model.addAttribute("userId", userId);
            model.addAttribute("username", username);
            model.addAttribute("displayName", displayName);
            model.addAttribute("selectedRole", role);
            UserSettings userSettings = userSettingsJdbcService.findByUserId(userId).orElse(UserSettings.defaultSettings(userId));
            model.addAttribute("selectedLanguage", userSettings.getSelectedLanguage());
            model.addAttribute("selectedUnitSystem", userSettings.getUnitSystem().name());
            model.addAttribute("preferColoredMap", userSettings.isPreferColoredMap());
            model.addAttribute("homeLatitude", userSettings.getHomeLatitude());
            model.addAttribute("homeLongitude", userSettings.getHomeLongitude());
        } else {
            // Default values for new users
            model.addAttribute("selectedLanguage", "en");
            model.addAttribute("selectedUnitSystem", "METRIC");
            model.addAttribute("preferColoredMap", false);
            model.addAttribute("selectedRole", "USER");
            model.addAttribute("homeLatitude", null);
            model.addAttribute("homeLongitude", null);
        }
        
        // Add available unit systems to model
        model.addAttribute("unitSystems", UnitSystem.values());
        
        // Check if user has avatar
        if (userId != null) {
            boolean hasAvatar = this.avatarService.getInfo(userId).isPresent();
            model.addAttribute("hasAvatar", hasAvatar);
        }
        
        // Add default avatars to model
        model.addAttribute("defaultAvatars", DEFAULT_AVATARS);
        
        // Add admin status to model
        model.addAttribute("isAdmin", ADMIN == currentUser.getRole());
        
        return "fragments/user-management :: user-form-page";
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
        
        return 0xff000000 | (r << 16) | (g << 8) | (b);
    }
    
    private void handleAvatarUpload(MultipartFile avatar, Long userId, Model model) {
        if (avatar != null && !avatar.isEmpty()) {
            try {
                // Validate file size
                if (avatar.getSize() > MAX_AVATAR_SIZE) {
                    model.addAttribute("avatarError", "Avatar file too large. Maximum size is 2MB.");
                    return;
                }
                
                // Validate content type
                String contentType = avatar.getContentType();
                if (contentType == null || !isAllowedContentType(contentType)) {
                    model.addAttribute("avatarError", "Invalid file type. Only JPEG, PNG, GIF, and WebP images are allowed.");
                    return;
                }
                
                byte[] imageData = avatar.getBytes();
                this.avatarService.updateAvatar(userId, contentType, imageData);

            } catch (IOException e) {
                model.addAttribute("avatarError", "Error processing avatar file: " + e.getMessage());
            }
        }
    }

    
    private boolean isAllowedContentType(String contentType) {
        for (String allowed : ALLOWED_CONTENT_TYPES) {
            if (allowed.equals(contentType)) {
                return true;
            }
        }
        return false;
    }
    
    private void handleDefaultAvatarSelection(String defaultAvatar, Long userId, Model model) {
        try {
            // Validate the default avatar selection
            if (!DEFAULT_AVATARS.contains(defaultAvatar)) {
                model.addAttribute("avatarError", "Invalid default avatar selection.");
                return;
            }
            
            // Load the default avatar from resources
            ClassPathResource resource = new ClassPathResource("static/img/avatars/default/" + defaultAvatar);
            if (!resource.exists()) {
                model.addAttribute("avatarError", "Default avatar file not found.");
                return;
            }
            
            byte[] imageData = resource.getInputStream().readAllBytes();
            String mimeType = "image/jpeg"; // All your defaults are .jpg

            this.avatarService.updateAvatar(userId, mimeType, imageData);

        } catch (IOException e) {
            model.addAttribute("avatarError", "Error processing default avatar: " + e.getMessage());
        }
    }
}
