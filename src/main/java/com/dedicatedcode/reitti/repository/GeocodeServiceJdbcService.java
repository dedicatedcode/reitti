package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.geocoding.RemoteGeocodeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GeocodeServiceJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public GeocodeServiceJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RemoteGeocodeService> GEOCODE_SERVICE_ROW_MAPPER = (rs, _) -> new RemoteGeocodeService(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("url_template"),
            rs.getBoolean("enabled"),
            rs.getInt("error_count"),
            rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toInstant() : null,
            rs.getTimestamp("last_error") != null ? rs.getTimestamp("last_error").toInstant() : null,
            GeocoderType.valueOf(rs.getString("type")),
            rs.getInt("priority"),
            rs.getLong("version")
    );

    public List<RemoteGeocodeService> findByEnabledTrueOrderByLastUsedAsc() {
        String sql = "SELECT * FROM geocode_services WHERE enabled = true ORDER BY last_used  NULLS FIRST";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public List<RemoteGeocodeService> findByEnabledTrueOrderByPriority() {
        String sql = "SELECT * FROM geocode_services WHERE enabled = true ORDER BY priority, name  NULLS FIRST";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public List<RemoteGeocodeService> findAllByOrderByNameAsc() {
        String sql = "SELECT * FROM geocode_services ORDER BY name";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public Optional<RemoteGeocodeService> findById(Long id) {
        String sql = "SELECT * FROM geocode_services WHERE id = ?";
        List<RemoteGeocodeService> results = jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public RemoteGeocodeService save(RemoteGeocodeService geocodeService) {
        if (geocodeService.getId() == null) {
            String sql = "INSERT INTO geocode_services (name, url_template, enabled, error_count, last_used, last_error, type, priority, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            Long id = jdbcTemplate.queryForObject(sql, Long.class,
                    geocodeService.getName(),
                    geocodeService.getUrlTemplate(),
                    geocodeService.isEnabled(),
                    geocodeService.getErrorCount(),
                    geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                    geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
                    geocodeService.getType().name(),
                    geocodeService.getPriority(),
                    geocodeService.getVersion()
            );
            return geocodeService.withId(id);
        } else {
            String sql = "UPDATE geocode_services SET name = ?, url_template = ?, enabled = ?, error_count = ?, last_used = ?, last_error = ?, type = ?, priority = ?, version = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    geocodeService.getName(),
                    geocodeService.getUrlTemplate(),
                    geocodeService.isEnabled(),
                    geocodeService.getErrorCount(),
                    geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                    geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
                    geocodeService.getType().name(),
                    geocodeService.getPriority(),
                    geocodeService.getVersion(),
                    geocodeService.getId()
            );
            return geocodeService;
        }
    }

    public void delete(RemoteGeocodeService geocodeService) {
        String sql = "DELETE FROM geocode_services WHERE id = ?";
        jdbcTemplate.update(sql, geocodeService.getId());
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM geocode_services";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}
