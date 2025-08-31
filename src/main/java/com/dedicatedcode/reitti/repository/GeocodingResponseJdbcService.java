package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.GeocodingResponse;
import com.dedicatedcode.reitti.model.SignificantPlace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GeocodingResponseJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public GeocodingResponseJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public void insert(GeocodingResponse geocodingResponse) {
        String sql = """
            INSERT INTO geocoding_response (significant_place_id, raw_data, provider_name, fetched_at, status, error_details)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, 
            geocodingResponse.getSignificantPlaceId(),
            geocodingResponse.getRawData(),
            geocodingResponse.getProviderName(),
            geocodingResponse.getFetchedAt(),
            geocodingResponse.getStatus().name(),
            geocodingResponse.getErrorDetails());
    }
    
    @Transactional(readOnly = true)
    public Optional<GeocodingResponse> findBySignificantPlace(SignificantPlace significantPlace) {
        String sql = """
            SELECT id, significant_place_id, raw_data, provider_name, fetched_at, status, error_details
            FROM geocoding_response 
            WHERE significant_place_id = ? 
            ORDER BY fetched_at DESC 
            LIMIT 1
            """;
        List<GeocodingResponse> results = jdbcTemplate.query(sql, new GeocodingResponseRowMapper(), significantPlace.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    private static class GeocodingResponseRowMapper implements RowMapper<GeocodingResponse> {
        @Override
        public GeocodingResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GeocodingResponse(
                rs.getLong("id"),
                rs.getLong("significant_place_id"),
                rs.getString("raw_data"),
                rs.getString("provider_name"),
                rs.getTimestamp("fetched_at").toInstant(),
                GeocodingResponse.GeocodingStatus.valueOf(rs.getString("status")),
                rs.getString("error_details")
            );
        }
    }
}
