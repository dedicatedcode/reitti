package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geocoding.RemoteGeocodeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class GeocodeServiceJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public GeocodeServiceJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RemoteGeocodeService> GEOCODE_SERVICE_ROW_MAPPER = new RowMapper<RemoteGeocodeService>() {
        @Override
        public RemoteGeocodeService mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RemoteGeocodeService(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("url_template"),
                    rs.getBoolean("enabled"),
                    rs.getInt("error_count"),
                    rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toInstant() : null,
                    rs.getTimestamp("last_error") != null ? rs.getTimestamp("last_error").toInstant() : null,
                    rs.getLong("version")
            );
        }
    };

    public List<RemoteGeocodeService> findByEnabledTrueOrderByLastUsedAsc() {
        String sql = "SELECT * " +
                "FROM geocode_services WHERE enabled = true ORDER BY last_used ASC NULLS FIRST";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public List<RemoteGeocodeService> findAllByOrderByNameAsc() {
        String sql = "SELECT * " +
                "FROM geocode_services ORDER BY name ASC";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public Optional<RemoteGeocodeService> findById(Long id) {
        String sql = "SELECT * " +
                "FROM geocode_services WHERE id = ?";
        List<RemoteGeocodeService> results = jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public RemoteGeocodeService save(RemoteGeocodeService geocodeService) {
        if (geocodeService.getId() == null) {
            // Insert new record
            String sql = "INSERT INTO geocode_services (name, url_template, enabled, error_count, last_used, last_error, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
            Long id = jdbcTemplate.queryForObject(sql, Long.class,
                    geocodeService.getName(),
                    geocodeService.getUrlTemplate(),
                    geocodeService.isEnabled(),
                    geocodeService.getErrorCount(),
                    geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                    geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
                    geocodeService.getVersion()
            );
            return geocodeService.withId(id);
        } else {
            // Update existing record
            String sql = "UPDATE geocode_services SET name = ?, url_template = ?, enabled = ?, error_count = ?, last_used = ?, last_error = ?, version = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    geocodeService.getName(),
                    geocodeService.getUrlTemplate(),
                    geocodeService.isEnabled(),
                    geocodeService.getErrorCount(),
                    geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                    geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
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

    public List<RemoteGeocodeService> findAll() {
        String sql = "SELECT * FROM geocode_services";
        return jdbcTemplate.query(sql, GEOCODE_SERVICE_ROW_MAPPER);
    }

    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM geocode_services WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM geocode_services";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}
