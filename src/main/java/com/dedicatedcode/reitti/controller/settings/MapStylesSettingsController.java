package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.map.ActiveMapStyleRequest;
import com.dedicatedcode.reitti.dto.map.MapStyleSettingsDTO;
import com.dedicatedcode.reitti.dto.map.SaveMapStyleRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.UserMapStyleValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/settings/map-styles")
public class MapStylesSettingsController {
    private final boolean dataManagementEnabled;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final UserMapStyleValidator userMapStyleValidator;
    private final ContextPathHolder contextPathHolder;

    public MapStylesSettingsController(
            @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
            UserMapStyleJdbcService userMapStyleJdbcService,
            UserMapStyleValidator userMapStyleValidator,
            ContextPathHolder contextPathHolder) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.userMapStyleValidator = userMapStyleValidator;
        this.contextPathHolder = contextPathHolder;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "map-styles");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        return "settings/map-styles";
    }

    @GetMapping("/api")
    @ResponseBody
    public MapStyleSettingsDTO getSettings(@AuthenticationPrincipal User user) {
        return userMapStyleJdbcService.getSettings(user, normalizedContextPath());
    }

    @PostMapping("/api")
    @ResponseBody
    public MapStyleSettingsDTO saveStyle(@AuthenticationPrincipal User user, @RequestBody SaveMapStyleRequest request) {
        UserMapStyle validatedStyle = userMapStyleValidator.validateAndNormalize(user, request);
        UserMapStyle style = userMapStyleJdbcService.save(user, validatedStyle);
        userMapStyleJdbcService.setActiveStyleId(user, style.frontendId());
        return userMapStyleJdbcService.getSettings(user, normalizedContextPath());
    }

    @PostMapping("/api/active")
    @ResponseBody
    public MapStyleSettingsDTO setActiveStyle(@AuthenticationPrincipal User user, @RequestBody ActiveMapStyleRequest request) {
        String styleId = request.activeStyleId();
        boolean valid = UserMapStyleJdbcService.DEFAULT_STYLE_ID.equals(styleId)
                || UserMapStyleJdbcService.resolveCustomId(styleId)
                        .flatMap(id -> userMapStyleJdbcService.findById(user, id))
                        .isPresent();
        if (!valid) {
            throw new IllegalArgumentException("Unknown style id: " + styleId);
        }
        userMapStyleJdbcService.setActiveStyleId(user, styleId);
        return userMapStyleJdbcService.getSettings(user, normalizedContextPath());
    }

    @DeleteMapping("/api/{id}")
    public ResponseEntity<Void> deleteStyle(@AuthenticationPrincipal User user, @PathVariable long id) {
        userMapStyleJdbcService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidMapStyle(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    private String normalizedContextPath() {
        String contextPath = contextPathHolder.getContextPath();
        return "/".equals(contextPath) ? "" : contextPath;
    }
}
