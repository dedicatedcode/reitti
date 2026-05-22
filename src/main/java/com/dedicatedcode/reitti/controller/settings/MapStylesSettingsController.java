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

import java.util.List;

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

        List<UserMapStyle> persisted = userMapStyleJdbcService.findAll(user);
        model.addAttribute("defaultStyle", UserMapStyle.defaultReittiStyle());
        model.addAttribute("styles", persisted);
        model.addAttribute("activeMapStyleId", userMapStyleJdbcService.getActiveStyleId(user));
        return "settings/map-styles";
    }

    @GetMapping("/add")
    public String addFragment(@AuthenticationPrincipal User user, @RequestParam String type, Model model) {
        model.addAttribute("type", type);
        return "settings/fragments/map-styles :: style-form";
    }

    @GetMapping("/style-form")
    public String loadStyleFormFragment(@AuthenticationPrincipal User user, @RequestParam String type, Model model) {
        return switch (type) {
            case "vector" -> "settings/fragments/map-styles :: vector-style-form";
            case "raster" -> "settings/fragments/map-styles :: raster-style-form";
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    @GetMapping("/style-form/vector")
    public String loadVectorStyleFormFragment(@AuthenticationPrincipal User user, @RequestParam String type, Model model) {
        return switch (type) {
            case "json" -> "settings/fragments/map-styles :: vector-style-json";
            case "url" -> "settings/fragments/map-styles :: vector-style-url";
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    @GetMapping("/style-form/raster")
    public String loadRasterStyleFormFragment(@AuthenticationPrincipal User user, @RequestParam String type, Model model) {
        return switch (type) {
            case "url-template" -> "settings/fragments/map-styles :: raster-style-url-template";
            case "json-url" -> "settings/fragments/map-styles :: raster-style-json-url";
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    @GetMapping("/api")
    @ResponseBody
    public MapStyleSettingsDTO getSettings(@AuthenticationPrincipal User user) {
        return userMapStyleJdbcService.getSettings(user, this.contextPathHolder.getContextPath());
    }

    @PostMapping("/api")
    @ResponseBody
    public MapStyleSettingsDTO saveStyle(@AuthenticationPrincipal User user, @RequestBody SaveMapStyleRequest request) {
        UserMapStyle validatedStyle = userMapStyleValidator.validateAndNormalize(user, request);
        UserMapStyle style = userMapStyleJdbcService.save(user, validatedStyle);
        userMapStyleJdbcService.setActiveStyleId(user, style.frontendId());
        return userMapStyleJdbcService.getSettings(user, this.contextPathHolder.getContextPath());
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
        return userMapStyleJdbcService.getSettings(user, this.contextPathHolder.getContextPath());
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

}
