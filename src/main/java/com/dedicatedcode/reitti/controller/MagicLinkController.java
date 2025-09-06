package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/settings/magic-links")
public class MagicLinkController {

    private final MagicLinkJdbcService magicLinkJdbcService;
    private final MessageSource messageSource;
    private final PasswordEncoder passwordEncoder;

    public MagicLinkController(MagicLinkJdbcService magicLinkJdbcService, MessageSource messageSource, PasswordEncoder passwordEncoder) {
        this.magicLinkJdbcService = magicLinkJdbcService;
        this.messageSource = messageSource;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String magicLinksContent(@AuthenticationPrincipal User user, Model model) {
        List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
        model.addAttribute("tokens", tokens);
        model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
        return "fragments/magic-links :: magic-links-content";
    }

    @PostMapping
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
                    return "fragments/magic-links :: magic-links-content";
                }
            }

            // Create token object
            MagicLinkToken token = new MagicLinkToken(null, name, tokenHash, accessLevel, expiryInstant, null, null, false);
            magicLinkJdbcService.create(user, token);

            // Build the full magic link URL
            String baseUrl = getBaseUrl(request);
            String magicLinkUrl = baseUrl + "/access?mt=" + rawToken;

            // Show the raw token once
            model.addAttribute("newTokenName", name);
            model.addAttribute("magicLinkUrl", magicLinkUrl);

            List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
            model.addAttribute("tokens", tokens);
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());

            return "fragments/magic-links :: magic-links-content";

        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("magic.links.create.error", e.getMessage()));
            
            List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(user);
            model.addAttribute("tokens", tokens);
            model.addAttribute("accessLevels", MagicLinkAccessLevel.values());
            
            return "fragments/magic-links :: magic-links-content";
        }
    }

    @PostMapping("/{id}/delete")
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

        return "fragments/magic-links :: magic-links-content";
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
