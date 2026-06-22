package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ApiTokenJdbcService;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.ApiTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static com.dedicatedcode.reitti.service.TimeUtil.adjustInstant;

@Controller
@RequestMapping("/settings/api-tokens")
public class ApiTokenSettingsController {
    private static final int MAX_TOKEN_USAGES = 10;

    private final ApiTokenService apiTokenService;
    private final ApiTokenJdbcService apiTokenJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final MessageSource messageSource;

    private final boolean dataManagementEnabled;

    public ApiTokenSettingsController(ApiTokenService apiTokenService, ApiTokenJdbcService apiTokenJdbcService,
                                      DeviceJdbcService deviceJdbcService,
                                      MessageSource messageSource,
                                      @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.apiTokenService = apiTokenService;
        this.apiTokenJdbcService = apiTokenJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                          Model model) {
        addCommonAttributes(timezone, user, model);
        model.addAttribute("activeSection", "api-tokens");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        return "settings/api-tokens";
    }

    @GetMapping("/usages")
    public String getTokenUsages(@AuthenticationPrincipal User user,
                                 @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                 Model model) {
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, 10)
                .stream()
                .map(t -> new ApiTokenUsageDTO(t.token(), t.name(), t.device(), adjustInstant(t.at(), timezone), t.endpoint(), t.ip()))
                .toList());
        model.addAttribute("maxUsagesToShow", 10);
        return "settings/api-tokens :: api-token-usages";
    }

    @PostMapping
    public String createToken(@AuthenticationPrincipal User user,
                              @RequestParam String name,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {
        try {
            apiTokenService.createToken(user, name);
            model.addAttribute("successMessage", getMessage("message.success.token.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.creation", e.getMessage()));
        }

        addCommonAttributes(timezone, user, model);
        return "settings/api-tokens :: api-tokens-content";
    }

    @PostMapping("/{id}/detach/{deviceId}")
    public String detachFromDevice(@AuthenticationPrincipal User user,
                              @PathVariable Long id,
                              @PathVariable Long deviceId,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {

        Optional<ApiToken> tokenById = this.apiTokenService.getTokenById(user, id);

        if (tokenById.isEmpty()) {
            throw new IllegalArgumentException("Token not found");
        }

        Optional<Device> deviceById = this.deviceJdbcService.find(user, deviceId);
        if (deviceById.isEmpty()) {
            throw new IllegalArgumentException("Device not found");
        }

        try {
            apiTokenJdbcService.save(tokenById.get().withDevice(null));
            model.addAttribute("successMessage", getMessage("message.success.token.detached", deviceById.get().name()));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.generic", e.getMessage()));
        }

        addCommonAttributes(timezone, user, model);
        return "settings/api-tokens :: api-tokens-content";
    }

    @PostMapping("/link/{id}")
    public String linkToDevice(@AuthenticationPrincipal User user,
                              @PathVariable Long id,
                              @RequestParam Long deviceId,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {

        Optional<ApiToken> tokenById = this.apiTokenService.getTokenById(user, id);

        if (tokenById.isEmpty()) {
            throw new IllegalArgumentException("Token not found");
        }

        Optional<Device> deviceById = this.deviceJdbcService.find(user, deviceId);
        if (deviceById.isEmpty()) {
            throw new IllegalArgumentException("Device not found");
        }

        try {
            apiTokenJdbcService.save(tokenById.get().withDevice(deviceById.get()));
            model.addAttribute("successMessage", getMessage("message.success.token.attach", deviceById.get().name()));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.generic", e.getMessage()));
        }
        addCommonAttributes(timezone, user, model);
        return "settings/api-tokens :: api-tokens-content";
    }

    @GetMapping("/{tokenId}/link-form")
    public String linkForm(@AuthenticationPrincipal User user,
                           @PathVariable Long tokenId,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                           Model model) {
        Optional<ApiToken> tokenById = this.apiTokenService.getTokenById(user, tokenId);
        if (tokenById.isEmpty()) {
            throw new IllegalArgumentException("Token not found");
        } else {
            model.addAttribute("devices", this.deviceJdbcService.getAll(user));
            model.addAttribute("token", toDto(timezone, tokenById.get()));
            return "settings/fragments/api-tokens :: link-form";
        }
    }

    @GetMapping("/tokens")
    public String tokensContent(@AuthenticationPrincipal User user,
                                @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                Model model) {
        addCommonAttributes(timezone, user, model);
        return "settings/api-tokens :: api-tokens-content";
    }

    @PostMapping("/{tokenId}/delete")
    public String deleteToken(@PathVariable Long tokenId,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              @AuthenticationPrincipal User user,
                              Model model) {

        try {
            apiTokenService.deleteToken(tokenId);
            model.addAttribute("successMessage", getMessage("message.success.token.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.token.deletion", e.getMessage()));
        }

        addCommonAttributes(timezone, user, model);
        return "settings/api-tokens :: api-tokens-content";
    }

    private void addCommonAttributes(ZoneId timezone, User user, Model model) {
        model.addAttribute("tokens", apiTokenService.getTokensForUser(user).stream()
                .map(t -> toDto(timezone, t)).toList());
        model.addAttribute("recentUsages", apiTokenService.getRecentUsagesForUser(user, MAX_TOKEN_USAGES));
        model.addAttribute("maxUsagesToShow", MAX_TOKEN_USAGES);
        model.addAttribute("devices", this.deviceJdbcService.getAll(user));
    }

    public record ApiTokenDto(Long id, Long deviceId, String deviceName, String token, String name, LocalDateTime createdAt, LocalDateTime lastUsedAt) {}

    public record ApiTokenUsageDTO(String token, String name, String device, LocalDateTime at, String endpoint, String ip) {
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private static ApiTokenDto toDto(ZoneId timezone, ApiToken t) {
        return new ApiTokenDto(t.getId(),
                               t.getDevice() != null ? t.getDevice().id() : null,
                               t.getDevice() != null ? t.getDevice().name() : null,
                               t.getToken(),
                               t.getName(),
                               adjustInstant(t.getCreatedAt(), timezone),
                               adjustInstant(t.getLastUsedAt(), timezone));
    }
}
