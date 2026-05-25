package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.map.MapStyleFormDTO;
import com.dedicatedcode.reitti.dto.map.MapStyleSettingsDTO;
import com.dedicatedcode.reitti.dto.map.SaveMapStyleRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.UserMapStyleValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.WebDataBinder;
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

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // empty strings from html inputs -> null (zoom etc)
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        MapStyleSettingsDTO settings = userMapStyleJdbcService.getSettings(user, normalizedContextPath());
        model.addAttribute("activeSection", "map-styles");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("activeStyleId", settings.activeStyleId());
        model.addAttribute("customStyles", settings.customStyles());
        return "settings/map-styles";
    }

    // htmx fragment endpoints

    @GetMapping("/form/new")
    public String openNewForm(@AuthenticationPrincipal User user, Model model) {
        populateFormModel(user, new MapStyleFormDTO(), false, null, model);
        return "fragments/map-styles :: style-form";
    }

    @GetMapping("/form/edit/{id}")
    public String openEditForm(@AuthenticationPrincipal User user, @PathVariable String id, Model model) {
        UserMapStyle style = resolveStyleForEdit(user, id);
        populateFormModel(user, toForm(style), true, null, model);
        return "fragments/map-styles :: style-form";
    }

    @GetMapping("/form/close")
    @ResponseBody
    public String closeForm() {
        return "";
    }

    @GetMapping("/type/{type}")
    public String getMapStyleTypePage(@PathVariable String type,
                                      @ModelAttribute("form") MapStyleFormDTO form) {
        if (!"raster".equals(type) && !"vector".equals(type)) {
            throw new IllegalArgumentException("Unknown map style type: " + type);
        }
        form.setMapType(type);
        return "fragments/map-styles :: style-configuration";
    }

    @GetMapping("/vector-input/{type}")
    public String getVectorInputPage(@PathVariable String type,
                                     @ModelAttribute("form") MapStyleFormDTO form) {
        if (!"url".equals(type) && !"json".equals(type)) {
            throw new IllegalArgumentException("Unknown vector style input type: " + type);
        }
        form.setStyleInputType(type);
        return "fragments/map-styles :: vector-input";
    }

    @GetMapping("/raster-input/{type}")
    public String getRasterInputPage(@PathVariable String type,
                                     @ModelAttribute("form") MapStyleFormDTO form) {
        if (!"tile_template".equals(type) && !"tilejson".equals(type)) {
            throw new IllegalArgumentException("Unknown raster source input type: " + type);
        }
        form.setRasterSourceInputType(type);
        return "fragments/map-styles :: raster-input";
    }

    @PostMapping("/save")
    public String saveStyleHtmx(@AuthenticationPrincipal User user,
                                @ModelAttribute("form") MapStyleFormDTO form,
                                HttpServletResponse response,
                                Model model) {
        try {
            SaveMapStyleRequest request = form.toSaveRequest();
            UserMapStyle validatedStyle = userMapStyleValidator.validateAndNormalize(user, request);
            UserMapStyle saved = userMapStyleJdbcService.save(user, validatedStyle);
            userMapStyleJdbcService.setActiveStyleId(user, saved.frontendId());
        } catch (IllegalArgumentException e) {
            boolean isEdit = form.getId() != null && !form.getId().isBlank();
            populateFormModel(user, form, isEdit, e.getMessage(), model);
            // validation failed - only re-render the form fragment, not the whole card
            response.setHeader("HX-Retarget", "#custom-map-style-form");
            response.setHeader("HX-Reswap", "outerHTML");
            return "fragments/map-styles :: style-form";
        }
        return renderSettingsCard(user, model);
    }

    @PostMapping("/active")
    @ResponseBody
    public ResponseEntity<Void> setActiveStyleHtmx(@AuthenticationPrincipal User user,
                                                   @RequestParam("activeStyleId") String activeStyleId) {
        validateActiveStyleId(user, activeStyleId);
        userMapStyleJdbcService.setActiveStyleId(user, activeStyleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public String deleteStyleHtmx(@AuthenticationPrincipal User user, @PathVariable String id, Model model) {
        long parsedId = UserMapStyleJdbcService.resolveCustomId(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown style id: " + id));
        userMapStyleJdbcService.delete(user, parsedId);
        return renderSettingsCard(user, model);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidMapStyle(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // helpers

    private void populateFormModel(User user, MapStyleFormDTO form, boolean isEdit, String errorMessage, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("errorMessage", errorMessage);
    }

    private String renderSettingsCard(User user, Model model) {
        MapStyleSettingsDTO settings = userMapStyleJdbcService.getSettings(user, normalizedContextPath());
        model.addAttribute("activeStyleId", settings.activeStyleId());
        model.addAttribute("customStyles", settings.customStyles());
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        return "fragments/map-styles :: settings-card";
    }

    private void validateActiveStyleId(User user, String activeStyleId) {
        boolean valid = UserMapStyleJdbcService.DEFAULT_STYLE_ID.equals(activeStyleId)
                || UserMapStyleJdbcService.resolveCustomId(activeStyleId)
                        .flatMap(id -> userMapStyleJdbcService.findById(user, id))
                        .isPresent();
        if (!valid) {
            throw new IllegalArgumentException("Unknown style id: " + activeStyleId);
        }
    }

    private UserMapStyle resolveStyleForEdit(User user, String frontendId) {
        long parsedId = UserMapStyleJdbcService.resolveCustomId(frontendId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown style id: " + frontendId));
        return userMapStyleJdbcService.findById(user, parsedId)
                .filter(s -> s.userId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown style id: " + frontendId));
    }

    private MapStyleFormDTO toForm(UserMapStyle style) {
        MapStyleFormDTO form = new MapStyleFormDTO();
        form.setId(style.frontendId());
        form.setLabel(style.name());
        form.setMapType(style.mapType());
        form.setStyleInputType(style.styleJson() != null ? "json" : style.styleInputType());
        form.setRasterSourceInputType(style.rasterSourceInputType());
        form.setStyleUrl(style.styleUrl());
        form.setStyleJson(style.styleJson());
        form.setShared(style.shared());
        MapStyleDataSource source = style.dataSource();
        if (source != null) {
            form.setProxyTiles(source.proxyTiles());
            form.setTileUrlTemplate(source.tileUrlTemplate());
            form.setTileJsonUrl(source.tileJsonUrl());
            if ("tilejson".equals(style.rasterSourceInputType())) {
                form.setRasterAttributionOverride(source.attribution());
            } else {
                form.setAttribution(source.attribution());
            }
            form.setMinzoom(source.minzoom());
            form.setMaxzoom(source.maxzoom());
            form.setTileSize(source.tileSize());
            form.setScheme(source.scheme());
        }
        MapStyleVectorOptions vectorOptions = style.vectorOptions();
        if (vectorOptions != null) {
            form.setAttributionOverride(vectorOptions.attributionOverride());
            form.setGlyphsUrlOverride(vectorOptions.glyphsUrlOverride());
            form.setSpriteUrlOverride(vectorOptions.spriteUrlOverride());
        }
        return form;
    }

    private String normalizedContextPath() {
        String contextPath = contextPathHolder.getContextPath();
        return "/".equals(contextPath) ? "" : contextPath;
    }
}
