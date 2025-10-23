package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.UserDto;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/share-access")
public class ShareAccessController {

    private final MagicLinkTokenService magicLinkTokenService;
    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final AvatarService avatarService;
    private final MessageSource messageSource;
    private final boolean dataManagementEnabled;

    public ShareAccessController(MagicLinkTokenService magicLinkTokenService,
                                 UserJdbcService userJdbcService,
                                 UserSharingJdbcService userSharingJdbcService,
                                 AvatarService avatarService,
                                 MessageSource messageSource,
                                 @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.magicLinkTokenService = magicLinkTokenService;
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.avatarService = avatarService;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String magicLinksContent(@AuthenticationPrincipal User user, Model model) {
        List<MagicLinkToken> tokens = magicLinkTokenService.getTokensForUser(user);
        model.addAttribute("tokens", tokens);
        model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
        model.addAttribute("activeSection", "sharing");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        List<UserDto> availableUsers = loadAvailableUsers(user);
        Set<Long> sharedUserIds = userSharingJdbcService.getSharedUserIds(user.getId());
        List<UserSharingDto> sharedWithMeUsers = loadSharedWithMeUsers(user);

        model.addAttribute("availableUsers", availableUsers);
        model.addAttribute("sharedUserIds", sharedUserIds);
        model.addAttribute("sharedWithMeUsers", sharedWithMeUsers);

        return "settings/share-access";
    }


    @PostMapping("/magic-links")
    public String createMagicLink(@AuthenticationPrincipal User user,
                                  @RequestParam String name,
                                  @RequestParam MagicLinkAccessLevel accessLevel,
                                  @RequestParam(required = false) String expiryDate,
                                  HttpServletRequest request,
                                  Model model) {
        try {
            // Calculate expiry date
            Instant expiryInstant = null;
            if (expiryDate != null && !expiryDate.trim().isEmpty()) {
                try {
                    LocalDate localDate = LocalDate.parse(expiryDate);
                    expiryInstant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                } catch (Exception e) {
                    model.addAttribute("errorMessage", getMessage("magic.links.invalid.date"));
                    List<MagicLinkToken> tokens = magicLinkTokenService.getTokensForUser(user);
                    model.addAttribute("tokens", tokens);
                    model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
                    return "settings/share-access :: magic-links-content";
                }
            }

            // Create token object
            String rawToken = magicLinkTokenService.createMapShareToken(user, name, accessLevel, expiryInstant);

            // Build the full magic link URL
            String baseUrl = getBaseUrl(request);
            String magicLinkUrl = baseUrl + "/access?mt=" + rawToken;

            model.addAttribute("newTokenName", name);
            model.addAttribute("magicLinkUrl", magicLinkUrl);

            model.addAttribute("tokens", magicLinkTokenService.getTokensForUser(user));
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());

            return "settings/share-access :: magic-links-content";

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("magic.links.create.error", e.getMessage()));

            model.addAttribute("tokens", magicLinkTokenService.getTokensForUser(user));
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
            
            return "settings/share-access :: magic-links-content";
        }
    }

    @PostMapping("/magic-links/{id}/delete")
    public String deleteMagicLink(@PathVariable long id,
                                  @AuthenticationPrincipal User user,
                                  Model model) {
        try {
            magicLinkTokenService.deleteToken(id);
            model.addAttribute("successMessage", getMessage("magic.links.deleted.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("magic.links.delete.error", e.getMessage()));
        }

        model.addAttribute("tokens", magicLinkTokenService.getTokensForUser(user));
        model.addAttribute("accessLevels", MagicLinkAccessLevel.values());

        return "settings/share-access :: magic-links-content";
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);
        return url.toString();
    }

    @PostMapping("/users")
    public String updateUserSharing(@AuthenticationPrincipal User user,
                                   @RequestParam(value = "sharedUserIds", required = false) List<Long> sharedUserIds,
                                   Model model) {
        try {
            if (sharedUserIds == null) {
                sharedUserIds = java.util.Collections.emptyList();
            }

            List<UserSharing> bySharingUser = this.userSharingJdbcService.findBySharingUser(user.getId());
            List<Long> finalSharedUserIds = sharedUserIds;

            Set<UserSharing> toDelete = bySharingUser.stream().filter(s -> !finalSharedUserIds.contains(s.getSharedWithUserId())).collect(Collectors.toSet());
            Set<UserSharing> toCreate = sharedUserIds.stream().filter(id -> bySharingUser.stream()
                    .noneMatch(s -> s.getSharedWithUserId().equals(id)))
                    .map(s -> new UserSharing(null, user.getId(), s, null, generateColorForUser(user.getId()), null))
                    .collect(Collectors.toSet());
            this.userSharingJdbcService.delete(toDelete);
            this.userSharingJdbcService.create(user, toCreate);
            model.addAttribute("shareSuccessMessage", getMessage("share-with.updated.success"));
        } catch (Exception e) {
            model.addAttribute("shareErrorMessage", getMessage("share-with.update.error", e.getMessage()));
        }

        List<UserDto> availableUsers = loadAvailableUsers(user);
        Set<Long> currentSharedUserIds = userSharingJdbcService.getSharedUserIds(user.getId());
        List<UserSharingDto> sharedWithMeUsers = loadSharedWithMeUsers(user);

        model.addAttribute("availableUsers", availableUsers);
        model.addAttribute("sharedUserIds", currentSharedUserIds);
        model.addAttribute("sharedWithMeUsers", sharedWithMeUsers);

        return "settings/share-access :: share-with-content";
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private List<UserDto> loadAvailableUsers(User user) {
        return userJdbcService.getAllUsers().stream()
                .filter(u -> !u.getId().equals(user.getId()))
                .map(u -> {
                    String currentUserAvatarUrl = this.avatarService.getInfo(u.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", u.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", u.getId()));
                    String currentUserInitials = this.avatarService.generateInitials(u.getDisplayName());
                    return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), currentUserAvatarUrl, currentUserInitials);
                }).toList();
    }

    @PostMapping("/shared-with-me/{id}/dismiss")
    public String dismissSharedAccess(@PathVariable Long id,
                                     @AuthenticationPrincipal User user,
                                     Model model) {
        try {
            userSharingJdbcService.dismissSharedAccess(id, user.getId());
            model.addAttribute("shareSuccessMessage", getMessage("shared-with-me.dismissed.success"));
        } catch (Exception e) {
            model.addAttribute("shareErrorMessage", getMessage("shared-with-me.dismiss.error", e.getMessage()));
        }

        // Reload data for the fragment
        List<UserDto> availableUsers = loadAvailableUsers(user);
        Set<Long> currentSharedUserIds = userSharingJdbcService.getSharedUserIds(user.getId());
        List<UserSharingDto> sharedWithMeUsers = loadSharedWithMeUsers(user);

        model.addAttribute("availableUsers", availableUsers);
        model.addAttribute("sharedUserIds", currentSharedUserIds);
        model.addAttribute("sharedWithMeUsers", sharedWithMeUsers);

        return "settings/share-access :: share-with-content";
    }

    @PostMapping("/shared-with-me/{id}/color")
    @ResponseBody
    public String updateSharingColor(@PathVariable Long id,
                                   @RequestParam String color,
                                   @AuthenticationPrincipal User user) {
        try {
            userSharingJdbcService.updateSharingColor(id, user.getId(), color);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    private List<UserSharingDto> loadSharedWithMeUsers(User user) {
        return userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .map(sharing -> {
                    User sharingUser = userJdbcService.findById(sharing.getSharingUserId())
                            .orElseThrow(() -> new RuntimeException("User not found: " + sharing.getSharingUserId()));
                    String avatarUrl = avatarService.getInfo(sharingUser.getId())
                            .map(avatarInfo -> String.format("/avatars/%d?ts=%s", sharingUser.getId(), avatarInfo.updatedAt()))
                            .orElse(String.format("/avatars/%d", sharingUser.getId()));
                    String avatarFallback = avatarService.generateInitials(sharingUser.getDisplayName());
                    UserDto sharingUserDto = new UserDto(sharingUser.getId(), sharingUser.getUsername(), 
                            sharingUser.getDisplayName(), avatarUrl, avatarFallback);
                    return new UserSharingDto(sharing.getId(), sharingUserDto, sharing.getColor(), sharing.getCreatedAt());
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private String generateColorForUser(Long userId) {
        String[] colors = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9",
            "#F8C471", "#82E0AA", "#F1948A", "#85C1E9", "#D7BDE2"
        };

        return colors[Math.abs(userId.hashCode()) % colors.length];
    }

    public record UserSharingDto(Long id, UserDto sharingUser, String color, java.time.Instant createdAt) {}
}
