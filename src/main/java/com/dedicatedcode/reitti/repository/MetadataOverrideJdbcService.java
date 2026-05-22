package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Repository
public class MetadataOverrideJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<MemoryMetadata> metadataRowMapper;

    public MetadataOverrideJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.metadataRowMapper = (rs, _) -> {
            try {
                MemoryMetadata metadata = new MemoryMetadata(rs.getTimestamp("start_time").toInstant(),
                                                             rs.getTimestamp("end_time").toInstant());
                Map<String, Object> properties = objectMapper.readValue(rs.getString("metadata"), new TypeReference<>() {});
                metadata.setProperties(properties);
                return metadata;
            } catch (Exception e) {
                throw new RuntimeException("Failed to decode JSONB metadata context", e);
            }
        };
    }

    private String toRangeLiteral(Instant start, Instant end) {
        return String.format("[%s,%s)", start.toString(), end.toString());
    }

    public Optional<MemoryMetadata> findBestOverlappingOverride(User user, Instant newStart, Instant newEnd) {
        String rangeParam = toRangeLiteral(newStart, newEnd);

        String sql = """
            SELECT lower(time_range) as start_time, upper(time_range) as end_time, metadata
            FROM location_metadata
            WHERE time_range && ?::tstzrange
              AND user_id = ?
            ORDER BY upper(time_range * ?::tstzrange) - lower(time_range * ?::tstzrange) DESC
            LIMIT 1
            """;

        try {
            MemoryMetadata match = jdbcTemplate.queryForObject(
                    sql,
                    metadataRowMapper,
                    rangeParam,
                    user.getId(),
                    rangeParam,
                    rangeParam
            );
            return Optional.ofNullable(match);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insertOverride(User user, String contextType, MemoryMetadata metadata) {
        String sql = """
            INSERT INTO location_metadata (user_id, context_type, time_range, metadata)
            VALUES (?, ?, ?::tstzrange, ?::jsonb)
            """;
        try {
            String rangeLiteral = toRangeLiteral(metadata.getStartTime(), metadata.getEndTime());
            String jsonPayload = objectMapper.writeValueAsString(metadata.getProperties());
            jdbcTemplate.update(sql, user.getId(), contextType, rangeLiteral, jsonPayload);
        } catch (Exception e) {
            throw new RuntimeException("Serialization mapping failed", e);
        }
    }

    public void updateOverridePayload(User user, MemoryMetadata metadata) {
        String sql = """
            UPDATE location_metadata
            SET metadata = ?::jsonb
            WHERE time_range = ?::tstzrange
              AND user_id = ?
            """;
        try {
            String rangeLiteral = toRangeLiteral(metadata.getStartTime(), metadata.getEndTime());
            String jsonPayload = objectMapper.writeValueAsString(metadata.getProperties());
            jdbcTemplate.update(sql, jsonPayload, rangeLiteral, user.getId());
        } catch (Exception e) {
            throw new RuntimeException("Serialization mapping failed", e);
        }
    }
}