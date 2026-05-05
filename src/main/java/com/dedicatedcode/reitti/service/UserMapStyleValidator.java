package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.map.SaveMapStyleRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.dedicatedcode.reitti.repository.UserMapStyleJdbcService.resolveCustomId;

@Service
public class UserMapStyleValidator {

    private final ObjectMapper objectMapper;
    private final MapStyleUrlValidator mapStyleUrlValidator;
    private final I18nService i18nService;

    public UserMapStyleValidator(
            ObjectMapper objectMapper,
            MapStyleUrlValidator mapStyleUrlValidator,
            I18nService i18nService) {
        this.objectMapper = objectMapper;
        this.mapStyleUrlValidator = mapStyleUrlValidator;
        this.i18nService = i18nService;
    }

    public UserMapStyle validateAndNormalize(User user, SaveMapStyleRequest request) {
        String label = clean(request.label());
        String mapType = normalizeChoice(request.mapType(), "vector", List.of("vector", "raster"));
        String styleInputType = normalizeChoice(request.styleInputType(), "url", List.of("url", "json"));
        String rasterSourceInputType = normalizeChoice(request.rasterSourceInputType(), "tile_template", List.of("tile_template", "tilejson"));
        String styleInput = clean(request.styleInput());
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException(message("error-name-required"));
        }

        String styleJson = null;
        String styleUrl = null;
        if ("vector".equals(mapType)) {
            if (!StringUtils.hasText(styleInput)) {
                throw new IllegalArgumentException(message("json".equals(styleInputType) ? "error-style-json-required" : "error-style-url-required"));
            }
            styleJson = "json".equals(styleInputType) || styleInput.startsWith("{") ? styleInput : null;
            styleUrl = styleJson == null ? styleInput : null;
            if (styleJson != null) {
                validateStyleJson(styleJson);
            } else {
                mapStyleUrlValidator.requireHttpUrl(styleUrl, label("style-json-url"));
            }
        }
        MapStyleDataSource source = normalizeDataSource(request.dataSource());
        MapStyleVectorOptions vectorOptions = normalizeVectorOptions(request.vectorOptions());
        boolean shared = request.shared() && user.getRole() == Role.ADMIN;
        if ("raster".equals(mapType)) {
            validateRasterSource(rasterSourceInputType, source);
            Integer tileSize = source.tileSize();
            String tileUrlTemplate = "tile_template".equals(rasterSourceInputType) ? normalizeRasterTileTemplate(source.tileUrlTemplate()) : null;
            source = new MapStyleDataSource(
                    "raster",
                    "raster",
                    "tilejson".equals(rasterSourceInputType) ? clean(source.tileJsonUrl()) : null,
                    tileUrlTemplate,
                    clean(source.attribution()),
                    source.minzoom(),
                    source.maxzoom(),
                    effectiveRasterTileSize(tileUrlTemplate, tileSize),
                    source.scheme(),
                    source.proxyTiles()
            );
        }
        if (source.proxyTiles() && user.getRole() != Role.ADMIN) {
            source = source.withProxyTiles(false);
        }

        Long parsedId = resolveCustomId(request.id()).orElse(null);

        return new UserMapStyle(
                parsedId,
                user.getId(),
                label,
                mapType,
                styleInputType,
                rasterSourceInputType,
                styleJson,
                styleUrl,
                source,
                vectorOptions,
                shared,
                null // version not handled here
        );
    }

    private MapStyleDataSource normalizeDataSource(MapStyleDataSource dataSource) {
        MapStyleDataSource source = dataSource != null ? dataSource : new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, false);
        String type = normalizeChoice(source.type(), "vector", List.of("vector", "raster", "raster-dem"));
        Integer tileSize = source.tileSize() == null || (source.tileSize() != 256 && source.tileSize() != 512) ? null : source.tileSize();
        String scheme = normalizeChoice(source.scheme(), null, List.of("xyz", "tms"));
        return new MapStyleDataSource(
                clean(source.sourceId()),
                type,
                clean(source.tileJsonUrl()),
                clean(source.tileUrlTemplate()),
                clean(source.attribution()),
                source.minzoom(),
                source.maxzoom(),
                tileSize,
                scheme,
                source.proxyTiles()
        );
    }

    private MapStyleVectorOptions normalizeVectorOptions(MapStyleVectorOptions options) {
        if (options == null) {
            return new MapStyleVectorOptions(null, null, null);
        }
        return new MapStyleVectorOptions(
                clean(options.attributionOverride()),
                clean(options.glyphsUrlOverride()),
                clean(options.spriteUrlOverride())
        );
    }

    private void validateRasterSource(String rasterSourceInputType, MapStyleDataSource source) {
        if ("tile_template".equals(rasterSourceInputType)) {
            String tileUrlTemplate = clean(source.tileUrlTemplate());
            if (!StringUtils.hasText(tileUrlTemplate) || !tileUrlTemplate.contains("{z}") || !tileUrlTemplate.contains("{x}") || !tileUrlTemplate.contains("{y}")) {
                throw new IllegalArgumentException(message("error-tile-template-placeholders"));
            }
            mapStyleUrlValidator.requireHttpTemplate(tileUrlTemplate, label("tile-template"));
        } else if (!StringUtils.hasText(source.tileJsonUrl())) {
            throw new IllegalArgumentException(message("error-tilejson-required"));
        } else {
            mapStyleUrlValidator.requireHttpUrl(source.tileJsonUrl(), label("tilejson-url"));
        }
        validateZoom(source.minzoom(), label("minzoom"));
        validateZoom(source.maxzoom(), label("maxzoom"));
        if (source.minzoom() != null && source.maxzoom() != null && source.minzoom() >= source.maxzoom()) {
            throw new IllegalArgumentException(message("error-zoom-order"));
        }
    }

    private void validateZoom(Integer zoom, String fieldName) {
        if (zoom != null && (zoom < 0 || zoom > 24)) {
            throw new IllegalArgumentException(message("error-zoom-range", fieldName));
        }
    }

    private void validateStyleJson(String styleJson) {
        try {
            objectMapper.readTree(styleJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(message("error-json"), e);
        }
    }

    private String normalizeRasterTileTemplate(String tileUrlTemplate) {
        String template = clean(tileUrlTemplate);
        if (!StringUtils.hasText(template)) {
            return null;
        }
        return template.replace("{r}", "@2x");
    }

    private Integer effectiveRasterTileSize(String tileUrlTemplate, Integer configuredTileSize) {
        if (StringUtils.hasText(tileUrlTemplate) && tileUrlTemplate.contains("@2x")) {
            return 256;
        }
        return configuredTileSize;
    }

    private String normalizeChoice(String value, String defaultValue, List<String> allowedValues) {
        String normalized = clean(value);
        if (!StringUtils.hasText(normalized)) {
            return defaultValue;
        }
        normalized = normalized.toLowerCase();
        return allowedValues.contains(normalized) ? normalized : defaultValue;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String label(String key) {
        return i18nService.translate("map.settings.dialog.map-styles." + key);
    }

    private String message(String key, Object... args) {
        return i18nService.translate("map.settings.dialog.map-styles." + key, args);
    }
}
