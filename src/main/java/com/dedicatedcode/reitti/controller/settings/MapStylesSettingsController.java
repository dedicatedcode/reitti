package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.map.MapStyleConfigDTO;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings/map-styles")
public class MapStylesSettingsController {
    private final boolean dataManagementEnabled;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final I18nService i18n;
    private final ObjectMapper objectMapper;

    public MapStylesSettingsController(
            @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
            UserMapStyleJdbcService userMapStyleJdbcService,
            I18nService i18n, ObjectMapper objectMapper) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.i18n = i18n;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "map-styles");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);

        List<MapStyleConfigDTO> persisted = userMapStyleJdbcService.findAll(user).stream().map(s -> s.toDto(user)).toList();
        model.addAttribute("styles", persisted);
        model.addAttribute("activeMapStyleId", userMapStyleJdbcService.getActiveStyleId(user));
        return "settings/map-styles";
    }

    @GetMapping("/clear")
    public String clearStyles(@AuthenticationPrincipal User user, Model model) {
        return "settings/map-styles :: empty-form-state";
    }

    @GetMapping("/form")
    public String addFragment(@AuthenticationPrincipal User user,
                              @RequestParam(required = false) Long id,
                              Model model) {
        if (id != null) {
            model.addAttribute("style", userMapStyleJdbcService.findById(user, id).map(s -> s.toDto(user)).orElseThrow(() -> new IllegalArgumentException("Unknown style id: " + id)));
        }
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);

        return "settings/fragments/map-styles :: style-form";
    }

    @PostMapping("/activate")
    public String activateStyle(@AuthenticationPrincipal User user, @RequestParam Long id, Model model) {
        if (this.userMapStyleJdbcService.findById(user, id).isEmpty()) {
            throw new IllegalStateException("Not allowed to use style with id [" + id + "]");
        }
        this.userMapStyleJdbcService.setActiveStyleId(user, id);

        List<MapStyleConfigDTO> persisted = userMapStyleJdbcService.findAll(user).stream().map(s -> s.toDto(user)).toList();
        model.addAttribute("styles", persisted);
        model.addAttribute("activeMapStyleId", userMapStyleJdbcService.getActiveStyleId(user));
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);

        return "settings/map-styles :: styles-table";
    }

    @PostMapping
    public String saveMapStyle(@AuthenticationPrincipal User user, @RequestParam Map<String, String> params, Model model, HttpServletResponse response) {
        Long id = params.get("id") != null ? Long.parseLong(params.get("id")) : null;
        if (id != null && this.userMapStyleJdbcService.findById(user, id).isEmpty()) {
            throw new IllegalStateException("Not allowed to use style with id [" + id + "]");
        }
        String mapType = params.get("mapType");
        String name = params.get("name");

        List<String> errors = new ArrayList<>();

        if (name == null || name.isBlank()) {
            errors.add(i18n.translate("map.settings.dialog.map-styles.error-name-required"));
        }

        if ("vector".equals(mapType)) {
            String styleInputType = params.get("styleInputType");
            if ("url".equals(styleInputType)) {
                String url = params.get("vectorStyleUrl");
                if (url == null || url.isBlank()) {
                    errors.add(i18n.translate("map.settings.dialog.map-styles.error-style-url-required"));
                }
            } else if ("json".equals(styleInputType)) {
                String json = params.get("vectorStyleJson");
                if (json == null || json.isBlank()) {
                    errors.add(i18n.translate("map.settings.dialog.map-styles.error-style-json-required"));
                }
                try {
                    objectMapper.readTree(json);
                } catch (Exception e) {
                    errors.add(i18n.translate("map.settings.dialog.map-styles.error-json"));
                }
            }
        } else if ("raster".equals(mapType)) {
            String rasterSourceInputType = params.get("rasterSourceInputType");
            if ("url-template".equals(rasterSourceInputType)) {
                String template = params.get("rasterTileTemplate");
                if (template == null || template.isBlank()) {
                    errors.add(i18n.translate("js.map.settings.dialog.map-styles.error-tile-template-required"));
                }
            } else if ("json-url".equals(rasterSourceInputType)) {
                String tileJsonUrl = params.get("rasterTileJsonUrl");
                if (tileJsonUrl == null || tileJsonUrl.isBlank()) {
                    errors.add(i18n.translate("js.map.settings.dialog.map-styles.error-tilejson-required"));
                }
            }
        }

        if (!errors.isEmpty()) {
            model.addAttribute("error", String.join("<br>", errors));
            response.setHeader("HX-Retarget", "#errors");
            return "settings/fragments/map-styles :: errors";
        }

        UserMapStyle mapStyle = buildFromParams(user, params);
        userMapStyleJdbcService.save(user, mapStyle);

        return getPage(user, model);
    }

    private UserMapStyle buildFromParams(User user, Map<String, String> params) {
        String id = params.get("id");
        String name = params.get("name");
        String mapType = params.get("mapType");
        String styleInputType = params.get("styleInputType");
        String rasterSourceInputType = params.get("rasterSourceInputType");
        String vectorStyleUrl = params.get("vectorStyleUrl");
        String vectorStyleJson = params.get("vectorStyleJson");
        String rasterTileTemplate = params.get("rasterTileTemplate");
        String rasterTileJsonUrl = params.get("rasterTileJsonUrl");
        String attributionOverride = params.get("attributionOverride");
        String glyphsUrlOverride = params.get("glyphsUrlOverride");
        String spriteUrlOverride = params.get("spriteUrlOverride");
        String minzoom = params.get("minzoom");
        String maxzoom = params.get("maxzoom");
        String tileSize = params.get("tileSize");
        String scheme = params.get("scheme");
        boolean proxyTiles = "on".equals(params.get("proxyTiles"));
        boolean shared = "on".equals(params.get("shared"));

        // Build the data source
        MapStyleDataSource dataSource = new MapStyleDataSource(
                null,
                mapType,
                rasterTileJsonUrl,
                rasterTileTemplate,
                attributionOverride,
                hasValue(minzoom) ? Integer.parseInt(minzoom) : null,
                hasValue(maxzoom) ? Integer.parseInt(maxzoom) : null,
                hasValue(tileSize) ? Integer.parseInt(tileSize) : null,
                scheme,
                proxyTiles
        );

        // Build vector options
        MapStyleVectorOptions vectorOptions = new MapStyleVectorOptions(
                attributionOverride,
                glyphsUrlOverride,
                spriteUrlOverride
        );

        // Determine the style URL and input based on map type
        String styleUrl = null;
        String styleInput = null;

        if ("vector".equals(mapType)) {
            if ("url".equals(styleInputType)) {
                styleUrl = vectorStyleUrl;
            } else {
                styleInput = vectorStyleJson;
            }
        }

        return new UserMapStyle(
                id != null ? Long.parseLong(id) : null,
                user.getId(),
                name,
                mapType,
                styleInputType,
                rasterSourceInputType,
                styleInput,
                styleUrl,
                dataSource,
                vectorOptions,
                false,
                shared,
                null);
    }

    @DeleteMapping
    public String deleteMapStyle(@AuthenticationPrincipal User user, @RequestParam Long id, Model model) {
        if (this.userMapStyleJdbcService.findById(user, id).isEmpty()) {
            throw new IllegalStateException("Not allowed to use style with id [" + id + "]");
        }

        this.userMapStyleJdbcService.delete(user, id);
        return getPage(user, model);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidMapStyle(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
