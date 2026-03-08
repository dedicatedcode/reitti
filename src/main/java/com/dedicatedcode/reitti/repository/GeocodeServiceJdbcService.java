package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GeocodeServiceJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public GeocodeServiceJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.geocodeServiceRowMapper = (rs, _) -> {
            try {
                String additionalParams = rs.getString("additional_params");
                return new GeocodeService(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("url"),
                        rs.getBoolean("enabled"),
                        rs.getInt("error_count"),
                        rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toInstant() : null,
                        rs.getTimestamp("last_error") != null ? rs.getTimestamp("last_error").toInstant() : null,
                        GeocoderType.valueOf(rs.getString("type")),
                        additionalParams != null ? objectMapper.readerForMapOf(String.class).readValue(additionalParams) : null,
                        rs.getInt("priority"),
                        rs.getLong("version"));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private final RowMapper<GeocodeService> geocodeServiceRowMapper;

    public List<GeocodeService> findByEnabledTrueOrderByPriority() {
        String sql = "SELECT * FROM geocode_services WHERE enabled = true ORDER BY priority, name";
        return jdbcTemplate.query(sql, geocodeServiceRowMapper);
    }

    public List<GeocodeService> findAllByOrderByNameAsc() {
        String sql = "SELECT * FROM geocode_services ORDER BY name";
        return jdbcTemplate.query(sql, geocodeServiceRowMapper);
    }

    public Optional<GeocodeService> findById(Long id) {
        String sql = "SELECT * FROM geocode_services WHERE id = ?";
        List<GeocodeService> results = jdbcTemplate.query(sql, geocodeServiceRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public GeocodeService save(GeocodeService geocodeService) {
        try {
            if (geocodeService.getId() == null) {
                String sql = "INSERT INTO geocode_services (name, url, enabled, error_count, last_used, last_error, type, priority, additional_params, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
                Long id = jdbcTemplate.queryForObject(sql, Long.class,
                  geocodeService.getName(),
                  geocodeService.getUrl(),
                  geocodeService.isEnabled(),
                  geocodeService.getErrorCount(),
                  geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                  geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
                  geocodeService.getType().name(),
                  geocodeService.getPriority(),
                  geocodeService.getAdditionalParameters() != null ? this.objectMapper.writeValueAsString(geocodeService.getAdditionalParameters()) : null,
                  geocodeService.getVersion()
                );

            return geocodeService.withId(id);
        } else {
            String sql = "UPDATE geocode_services SET name = ?, url = ?, enabled = ?, error_count = ?, last_used = ?, last_error = ?, type = ?, priority = ?, additional_params = ?, version = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    geocodeService.getName(),
                    geocodeService.getUrl(),
                    geocodeService.isEnabled(),
                    geocodeService.getErrorCount(),
                    geocodeService.getLastUsed() != null ? java.sql.Timestamp.from(geocodeService.getLastUsed()) : null,
                    geocodeService.getLastError() != null ? java.sql.Timestamp.from(geocodeService.getLastError()) : null,
                    geocodeService.getType().name(),
                    geocodeService.getPriority(),
                    geocodeService.getAdditionalParameters() != null ? this.objectMapper.writeValueAsString(geocodeService.getAdditionalParameters()) : null,
                    geocodeService.getVersion(),
                    geocodeService.getId()
            );
            return geocodeService;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(GeocodeService geocodeService) {
        String sql = "DELETE FROM geocode_services WHERE id = ?";
        jdbcTemplate.update(sql, geocodeService.getId());
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM geocode_services";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}
