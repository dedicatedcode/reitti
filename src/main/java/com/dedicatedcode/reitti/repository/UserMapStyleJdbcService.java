package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class UserMapStyleJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public UserMapStyleJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserMapStyle> rowMapper = (rs, _) -> new UserMapStyle(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("name"),
            rs.getString("map_type"),
            rs.getString("style_input_type"),
            rs.getString("raster_source_input_type"),
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
            rs.getBoolean("default_style"),
            rs.getLong("version")
    );

    public List<UserMapStyle> findAll(User user) {
        List<UserMapStyle> query = jdbcTemplate.query(
                "SELECT * FROM user_map_styles WHERE user_id = ? OR shared = TRUE ORDER BY shared, name, id",
                rowMapper,
                user.getId()
        );
        return query.stream().sorted(Comparator.comparing(UserMapStyle::defaultStyle).reversed().thenComparing(UserMapStyle::id)).toList();
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

    public Long getActiveStyleId(User user) {
        List<Long> results = jdbcTemplate.queryForList(
                "SELECT active_style_id FROM user_map_style_settings WHERE user_id = ?",
                Long.class,
                user.getId()
        );
        return results.getFirst();
    }

    @Transactional
    public void setActiveStyleId(User user, Long activeStyleId) {
        jdbcTemplate.update("""
                INSERT INTO user_map_style_settings (user_id, active_style_id)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET active_style_id = EXCLUDED.active_style_id, updated_at = CURRENT_TIMESTAMP
                """, user.getId(), activeStyleId);
    }

    @Transactional
    @CacheEvict(cacheNames = {"mapStyleJson", "mapStyles"}, allEntries = true)
    public UserMapStyle save(User user, UserMapStyle style) {
        if (style.id() != null) {
            Optional<UserMapStyle> existing = findById(user, style.id());
            if (existing.isPresent() && existing.get().defaultStyle()) {
                throw new UnsupportedOperationException("Default styles cannot be modified.");
            }
        }

        if (style.id() != null && findOwnedById(user, style.id()).isPresent()) {
            jdbcTemplate.update("""
                    UPDATE user_map_styles
                    SET name = ?, map_type = ?, style_input_type = ?, raster_source_input_type = ?,
                        style_json = ?, style_url = ?, source_id = ?, source_type = ?, tilejson_url = ?,
                        tile_url_template = ?, attribution = ?, minzoom = ?, maxzoom = ?, tile_size = ?, scheme = ?,
                        proxy_tiles = ?, attribution_override = ?, glyphs_url_override = ?, sprite_url_override = ?,
                        shared = ?, default_style = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
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
                    style.defaultStyle(),
                    user.getId(),
                    style.id());
            return findOwnedById(user, style.id()).orElseThrow();
        }

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO user_map_styles
                    (user_id, name, map_type, style_input_type, raster_source_input_type, style_json, style_url,
                     source_id, source_type, tilejson_url, tile_url_template, attribution, minzoom, maxzoom, tile_size, scheme,
                     proxy_tiles, attribution_override, glyphs_url_override, sprite_url_override, shared, default_style)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                style.shared(),
                style.defaultStyle());
        return findOwnedById(user, id).orElseThrow();
    }

    @Transactional
    @CacheEvict(cacheNames = {"mapStyleJson", "mapStyles"}, allEntries = true)
    public void delete(User user, long id) {
        // Prevent deletion of default styles (they are not owned by any user anyway)
        jdbcTemplate.update("DELETE FROM user_map_styles WHERE user_id = ? AND id = ?", user.getId(), id);
        jdbcTemplate.update(
                "UPDATE user_map_style_settings SET active_style_id = (SELECT CAST(id AS TEXT) FROM user_map_styles WHERE name = 'Reitti' LIMIT 1) WHERE active_style_id = ?", id);
    }
}
