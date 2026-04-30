package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.map.MapStyleConfigDTO;
import com.dedicatedcode.reitti.dto.map.MapStyleSettingsDTO;
import com.dedicatedcode.reitti.dto.map.SaveMapStyleRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.RemoteTileUrlValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class UserMapStyleJdbcService {
    private static final String DEFAULT_STYLE_ID = "reitti";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RemoteTileUrlValidator remoteTileUrlValidator;
    private final I18nService i18nService;

    public UserMapStyleJdbcService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RemoteTileUrlValidator remoteTileUrlValidator,
            I18nService i18nService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.remoteTileUrlValidator = remoteTileUrlValidator;
        this.i18nService = i18nService;
    }

    private final RowMapper<UserMapStyle> rowMapper = (rs, _) -> new UserMapStyle(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("name"),
            defaultText(rs.getString("map_type"), "vector"),
            defaultText(rs.getString("style_input_type"), "url"),
            defaultText(rs.getString("raster_source_input_type"), "tile_template"),
            rs.getString("style_json"),
            rs.getString("style_url"),
            new MapStyleDataSource(
                    rs.getString("source_id"),
                    rs.getString("source_type"),
                    rs.getString("tilejson_url"),
                    rs.getString("tile_url_template"),
                    rs.getString("attribution"),
                    (Integer) rs.getObject("minzoom"),
                    (Integer) rs.getObject("maxzoom"),
                    (Integer) rs.getObject("tile_size"),
                    rs.getString("scheme")
            ),
            new MapStyleVectorOptions(
                    rs.getString("attribution_override"),
                    rs.getString("glyphs_url_override"),
                    rs.getString("sprite_url_override")
            ),
            rs.getBoolean("shared"),
            rs.getLong("version")
    );

    public List<UserMapStyle> findAll(User user) {
        return jdbcTemplate.query(
                "SELECT * FROM user_map_styles WHERE user_id = ? OR shared = TRUE ORDER BY shared, name, id",
                rowMapper,
                user.getId()
        );
    }

    public Optional<UserMapStyle> findById(User user, long id) {
        List<UserMapStyle> results = jdbcTemplate.query(
                "SELECT * FROM user_map_styles WHERE id = ? AND (user_id = ? OR shared = TRUE)",
                rowMapper,
                id,
                user.getId()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private Optional<UserMapStyle> findOwnedById(User user, long id) {
        List<UserMapStyle> results = jdbcTemplate.query(
                "SELECT * FROM user_map_styles WHERE user_id = ? AND id = ?",
                rowMapper,
                user.getId(),
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public String getActiveStyleId(User user) {
        List<String> results = jdbcTemplate.queryForList(
                "SELECT active_style_id FROM user_map_style_settings WHERE user_id = ?",
                String.class,
                user.getId()
        );
        if (results.isEmpty()) {
            return DEFAULT_STYLE_ID;
        }
        String activeStyleId = results.getFirst();
        if (isValidStyleId(user, activeStyleId)) {
            return activeStyleId;
        }
        setActiveStyleId(user, DEFAULT_STYLE_ID);
        return DEFAULT_STYLE_ID;
    }

    @Transactional
    public void setActiveStyleId(User user, String activeStyleId) {
        String safeActiveStyleId = isValidStyleId(user, activeStyleId)
                ? activeStyleId
                : DEFAULT_STYLE_ID;
        jdbcTemplate.update("""
                INSERT INTO user_map_style_settings (user_id, active_style_id)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET active_style_id = EXCLUDED.active_style_id, updated_at = CURRENT_TIMESTAMP
                """, user.getId(), safeActiveStyleId);
    }

    private boolean isValidStyleId(User user, String styleId) {
        return DEFAULT_STYLE_ID.equals(styleId) || resolveCustomId(styleId).flatMap(id -> findById(user, id)).isPresent();
    }

    @Transactional
    public UserMapStyle save(User user, SaveMapStyleRequest request) {
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
                remoteTileUrlValidator.requireHttpUrl(styleUrl, label("style-json-url"));
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
                    "custom-raster-source",
                    "raster",
                    "tilejson".equals(rasterSourceInputType) ? clean(source.tileJsonUrl()) : null,
                    tileUrlTemplate,
                    clean(source.attribution()),
                    source.minzoom(),
                    source.maxzoom(),
                    effectiveRasterTileSize(tileUrlTemplate, tileSize),
                    source.scheme()
            );
        }
        validateNoPrivateUrlsForStandardUser(user, styleUrl, styleJson, source, vectorOptions);

        Optional<Long> existingId = resolveCustomId(request.id());
        if (existingId.isPresent() && findOwnedById(user, existingId.get()).isPresent()) {
            jdbcTemplate.update("""
                    UPDATE user_map_styles
                    SET name = ?, map_type = ?, style_input_type = ?, raster_source_input_type = ?,
                        style_json = ?, style_url = ?, source_id = ?, source_type = ?, tilejson_url = ?,
                        tile_url_template = ?, attribution = ?, minzoom = ?, maxzoom = ?, tile_size = ?, scheme = ?,
                        attribution_override = ?, glyphs_url_override = ?, sprite_url_override = ?, shared = ?,
                        updated_at = CURRENT_TIMESTAMP, version = version + 1
                    WHERE user_id = ? AND id = ?
                    """,
                    label,
                    mapType,
                    styleInputType,
                    rasterSourceInputType,
                    styleJson,
                    styleUrl,
                    clean(source.sourceId()),
                    clean(source.type()),
                    clean(source.tileJsonUrl()),
                    clean(source.tileUrlTemplate()),
                    clean(source.attribution()),
                    source.minzoom(),
                    source.maxzoom(),
                    source.tileSize(),
                    source.scheme(),
                    clean(vectorOptions.attributionOverride()),
                    clean(vectorOptions.glyphsUrlOverride()),
                    clean(vectorOptions.spriteUrlOverride()),
                    shared,
                    user.getId(),
                    existingId.get());
            return findOwnedById(user, existingId.get()).orElseThrow();
        }

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO user_map_styles
                    (user_id, name, map_type, style_input_type, raster_source_input_type, style_json, style_url,
                     source_id, source_type, tilejson_url, tile_url_template, attribution, minzoom, maxzoom, tile_size, scheme,
                     attribution_override, glyphs_url_override, sprite_url_override, shared)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                user.getId(),
                label,
                mapType,
                styleInputType,
                rasterSourceInputType,
                styleJson,
                styleUrl,
                clean(source.sourceId()),
                clean(source.type()),
                clean(source.tileJsonUrl()),
                clean(source.tileUrlTemplate()),
                clean(source.attribution()),
                source.minzoom(),
                source.maxzoom(),
                source.tileSize(),
                source.scheme(),
                clean(vectorOptions.attributionOverride()),
                clean(vectorOptions.glyphsUrlOverride()),
                clean(vectorOptions.spriteUrlOverride()),
                shared);
        return findOwnedById(user, id).orElseThrow();
    }

    @Transactional
    public void delete(User user, long id) {
        jdbcTemplate.update("DELETE FROM user_map_styles WHERE user_id = ? AND id = ?", user.getId(), id);
        if (("custom-" + id).equals(getActiveStyleId(user))) {
            setActiveStyleId(user, DEFAULT_STYLE_ID);
        }
    }

    public MapStyleSettingsDTO getSettings(User user, String contextPath) {
        return new MapStyleSettingsDTO(
                getActiveStyleId(user),
                findAll(user).stream().map(style -> toDto(user, style, contextPath)).toList()
        );
    }

    public MapStyleConfigDTO toDto(User user, UserMapStyle style, String contextPath) {
        return new MapStyleConfigDTO(
                style.frontendId(),
                style.name(),
                style.mapType(),
                StringUtils.hasText(style.styleJson()) ? "json" : style.styleInputType(),
                style.rasterSourceInputType(),
                styleUrlForClient(style, contextPath),
                style.styleInput(),
                true,
                style.shared(),
                style.userId().equals(user.getId()),
                style.dataSource(),
                style.vectorOptions()
        );
    }

    public static Optional<Long> resolveCustomId(String frontendId) {
        if (!StringUtils.hasText(frontendId) || !frontendId.startsWith("custom-")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(frontendId.substring("custom-".length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private MapStyleDataSource normalizeDataSource(MapStyleDataSource dataSource) {
        MapStyleDataSource source = dataSource != null ? dataSource : new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null);
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
                scheme
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
            remoteTileUrlValidator.requireHttpTemplate(tileUrlTemplate, label("tile-template"));
        } else if (!StringUtils.hasText(source.tileJsonUrl())) {
            throw new IllegalArgumentException(message("error-tilejson-required"));
        } else {
            remoteTileUrlValidator.requireHttpUrl(source.tileJsonUrl(), label("tilejson-url"));
        }
        validateZoom(source.minzoom(), label("minzoom"));
        validateZoom(source.maxzoom(), label("maxzoom"));
        if (source.minzoom() != null && source.maxzoom() != null && source.minzoom() >= source.maxzoom()) {
            throw new IllegalArgumentException(message("error-zoom-order"));
        }
    }

    private void validateNoPrivateUrlsForStandardUser(
            User user,
            String styleUrl,
            String styleJson,
            MapStyleDataSource source,
            MapStyleVectorOptions vectorOptions) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        rejectPrivateUrl(styleUrl);
        rejectPrivateUrl(source.tileJsonUrl());
        rejectPrivateTemplate(source.tileUrlTemplate());
        rejectPrivateTemplate(vectorOptions.glyphsUrlOverride());
        rejectPrivateUrl(vectorOptions.spriteUrlOverride());

        if (StringUtils.hasText(styleJson)) {
            try {
                rejectPrivateUrlsInJson(objectMapper.readTree(styleJson));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(message("error-json"), e);
            }
        }
    }

    private void rejectPrivateUrlsInJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            rejectPrivateUrl(value);
            rejectPrivateTemplate(value);
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(this::rejectPrivateUrlsInJson);
        }
    }

    private void rejectPrivateUrl(String value) {
        if (StringUtils.hasText(value) && startsWithHttp(value) && remoteTileUrlValidator.isValidLocalUrl(value)) {
            throw new IllegalArgumentException(message("error-local-url"));
        }
    }

    private void rejectPrivateTemplate(String value) {
        if (StringUtils.hasText(value) && startsWithHttp(value) && remoteTileUrlValidator.isValidLocalTemplate(value)) {
            throw new IllegalArgumentException(message("error-local-url"));
        }
    }

    private boolean startsWithHttp(String value) {
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
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

    private String styleUrlForClient(UserMapStyle style, String contextPath) {
        if ("vector".equals(style.mapType())
                && !StringUtils.hasText(style.styleJson())
                && StringUtils.hasText(style.styleUrl())
                && !remoteTileUrlValidator.isServerFetchAllowedUrl(style.styleUrl())) {
            return style.styleUrl();
        }
        return contextPath + "/map/custom/" + style.id() + ".json?v=" + style.version();
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
        return i18nService.translate("js.map.settings.dialog.map-styles." + key);
    }

    private String message(String key, Object... args) {
        return i18nService.translate("js.map.settings.dialog.map-styles." + key, args);
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
