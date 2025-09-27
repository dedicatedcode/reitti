package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/settings/share-access")
public class ShareAccessController {

    private final MagicLinkJdbcService magicLinkJdbcService;
    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final MessageSource messageSource;
    private final PasswordEncoder passwordEncoder;
    private final boolean dataManagementEnabled;

    public ShareAccessController(MagicLinkJdbcService magicLinkJdbcService,
                                 UserJdbcService userJdbcService,
                                 UserSharingJdbcService userSharingJdbcService,
                                 MessageSource messageSource,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.messageSource = messageSource;
        this.passwordEncoder = passwordEncoder;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String magicLinksContent(@AuthenticationPrincipal User user, Model model) {
        List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
        model.addAttribute("tokens", tokens);
        model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
        model.addAttribute("activeSection", "sharing");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
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
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = passwordEncoder.encode(rawToken);

            // Calculate expiry date
            Instant expiryInstant = null;
            if (expiryDate != null && !expiryDate.trim().isEmpty()) {
                try {
                    LocalDate localDate = LocalDate.parse(expiryDate);
                    expiryInstant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                } catch (Exception e) {
                    model.addAttribute("errorMessage", getMessage("magic.links.invalid.date"));
                    List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
                    model.addAttribute("tokens", tokens);
                    model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
                    return "settings/share-access :: magic-links-content";
                }
            }

            // Create token object
            MagicLinkToken token = new MagicLinkToken(null, name, tokenHash, accessLevel, expiryInstant, null, null, false);
            magicLinkJdbcService.create(user, token);

            // Build the full magic link URL
            String baseUrl = getBaseUrl(request);
            String magicLinkUrl = baseUrl + "/access?mt=" + rawToken;

            model.addAttribute("newTokenName", name);
            model.addAttribute("magicLinkUrl", magicLinkUrl);

            List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
            model.addAttribute("tokens", tokens);
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());

            return "settings/share-access :: magic-links-content";

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("magic.links.create.error", e.getMessage()));
            
            List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
            model.addAttribute("tokens", tokens);
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
            
            return "settings/share-access :: magic-links-content";
        }
    }

    @PostMapping("/magic-links/{id}/delete")
    public String deleteMagicLink(@PathVariable long id,
                                  @AuthenticationPrincipal User user,
                                  Model model) {
        try {
            magicLinkJdbcService.delete(id);
            model.addAttribute("successMessage", getMessage("magic.links.deleted.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("magic.links.delete.error", e.getMessage()));
        }

        List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
        model.addAttribute("tokens", tokens);
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

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
