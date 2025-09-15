package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/settings/api-tokens")
public class ApiTokenSettingsController {
    private final ApiTokenService apiTokenService;
    private final MessageSource messageSource;

    private final boolean dataManagementEnabled;

    public ApiTokenSettingsController(ApiTokenService apiTokenService,
                                      MessageSource messageSource,
                                      @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.apiTokenService = apiTokenService;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "api-tokens");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("tokens", apiTokenService.getTokensForUser(user));
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);
        return "settings/api-tokens";
    }

    @PostMapping
    public String createToken(@AuthenticationPrincipal User user, @RequestParam String name, Model model) {
        try {
            apiTokenService.createToken(user, name);
            model.addAttribute("successMessage", getMessage("message.success.token.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.creation", e.getMessage()));
        }

        // Get updated token list and add to model
        List<ApiToken> tokens = apiTokenService.getTokensForUser(user);
        model.addAttribute("tokens", tokens);
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);

        // Return the api-tokens-content fragment
        return "settings/api-tokens :: api-tokens-content";
    }

    @PostMapping("/{tokenId}/delete")
    public String deleteToken(@PathVariable Long tokenId, @AuthenticationPrincipal User user, Model model) {

        try {
            apiTokenService.deleteToken(tokenId);
            model.addAttribute("successMessage", getMessage("message.success.token.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.deletion", e.getMessage()));
        }

        // Get updated token list and add to model
        List<ApiToken> tokens = apiTokenService.getTokensForUser(user);
        model.addAttribute("tokens", tokens);
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10));
        model.addAttribute("maxUsagesToShow", 10);

        // Return the api-tokens-content fragment
        return "settings/api-tokens :: api-tokens-content";
    }


    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
