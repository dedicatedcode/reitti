package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.map.MapStyleConfigDTO;
import com.dedicatedcode.reitti.dto.map.MapStyleSettingsDTO;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserMapStyleJdbcService {
    private static final String DEFAULT_STYLE_ID = "reitti";

    private final JdbcTemplate jdbcTemplate;

    public UserMapStyleJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                    rs.getString("scheme"),
                    rs.getBoolean("proxy_tiles")
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
        String storedActiveStyleId = isValidStyleId(user, activeStyleId)
                ? activeStyleId
                : DEFAULT_STYLE_ID;
        jdbcTemplate.update("""
                INSERT INTO user_map_style_settings (user_id, active_style_id)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET active_style_id = EXCLUDED.active_style_id, updated_at = CURRENT_TIMESTAMP
                """, user.getId(), storedActiveStyleId);
    }

    private boolean isValidStyleId(User user, String styleId) {
        return DEFAULT_STYLE_ID.equals(styleId) || resolveCustomId(styleId).flatMap(id -> findById(user, id)).isPresent();
    }

    @Transactional
    public UserMapStyle save(User user, UserMapStyle style) {
        if (style.id() != null && findOwnedById(user, style.id()).isPresent()) {
            jdbcTemplate.update("""
                    UPDATE user_map_styles
                    SET name = ?, map_type = ?, style_input_type = ?, raster_source_input_type = ?,
                        style_json = ?, style_url = ?, source_id = ?, source_type = ?, tilejson_url = ?,
                        tile_url_template = ?, attribution = ?, minzoom = ?, maxzoom = ?, tile_size = ?, scheme = ?,
                        proxy_tiles = ?, attribution_override = ?, glyphs_url_override = ?, sprite_url_override = ?, shared = ?,
                        updated_at = CURRENT_TIMESTAMP, version = version + 1
                    WHERE user_id = ? AND id = ?
                    """,
                    style.name(),
                    style.mapType(),
                    style.styleInputType(),
                    style.rasterSourceInputType(),
                    style.styleJson(),
                    style.styleUrl(),
                    style.dataSource().sourceId(),
                    style.dataSource().type(),
                    style.dataSource().tileJsonUrl(),
                    style.dataSource().tileUrlTemplate(),
                    style.dataSource().attribution(),
                    style.dataSource().minzoom(),
                    style.dataSource().maxzoom(),
                    style.dataSource().tileSize(),
                    style.dataSource().scheme(),
                    style.dataSource().proxyTiles(),
                    style.vectorOptions().attributionOverride(),
                    style.vectorOptions().glyphsUrlOverride(),
                    style.vectorOptions().spriteUrlOverride(),
                    style.shared(),
                    user.getId(),
                    style.id());
            return findOwnedById(user, style.id()).orElseThrow();
        }

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO user_map_styles
                    (user_id, name, map_type, style_input_type, raster_source_input_type, style_json, style_url,
                     source_id, source_type, tilejson_url, tile_url_template, attribution, minzoom, maxzoom, tile_size, scheme,
                     proxy_tiles, attribution_override, glyphs_url_override, sprite_url_override, shared)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                user.getId(),
                style.name(),
                style.mapType(),
                style.styleInputType(),
                style.rasterSourceInputType(),
                style.styleJson(),
                style.styleUrl(),
                style.dataSource().sourceId(),
                style.dataSource().type(),
                style.dataSource().tileJsonUrl(),
                style.dataSource().tileUrlTemplate(),
                style.dataSource().attribution(),
                style.dataSource().minzoom(),
                style.dataSource().maxzoom(),
                style.dataSource().tileSize(),
                style.dataSource().scheme(),
                style.dataSource().proxyTiles(),
                style.vectorOptions().attributionOverride(),
                style.vectorOptions().glyphsUrlOverride(),
                style.vectorOptions().spriteUrlOverride(),
                style.shared());
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
                style.styleJson() != null ? "json" : style.styleInputType(),
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
        if (frontendId == null || frontendId.isBlank() || !frontendId.startsWith("custom-")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(frontendId.substring("custom-".length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String styleUrlForClient(UserMapStyle style, String contextPath) {
        return contextPath + "/map/custom/" + style.id() + ".json?v=" + style.version();
    }

    private static String defaultText(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
