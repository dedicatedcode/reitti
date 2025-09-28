package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geocoding.GeocodingResponse;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

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
            VALUES (?, to_json(?::json), ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                geocodingResponse.getSignificantPlaceId(),
                geocodingResponse.getRawData(),
                geocodingResponse.getProviderName(),
                Timestamp.from(geocodingResponse.getFetchedAt()),
                geocodingResponse.getStatus().name(),
                geocodingResponse.getErrorDetails());
    }
    
    @Transactional(readOnly = true)
    public List<GeocodingResponse> findBySignificantPlace(SignificantPlace significantPlace) {
        String sql = """
            SELECT id, significant_place_id, raw_data, provider_name, fetched_at, status, error_details
            FROM geocoding_response
            WHERE significant_place_id = ?
            ORDER BY fetched_at DESC
            """;
        return jdbcTemplate.query(sql, new GeocodingResponseRowMapper(), significantPlace.getId());
    }

    public void deleteAllForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM geocoding_response WHERE significant_place_id IN (SELECT id FROM significant_places WHERE user_id = ?)", user.getId());
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
