package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
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
        this.metadataRowMapper = (rs, rowNum) -> {
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

    // RowMapper pulls the range boundaries using Postgres functions seamlessly

    /**
     * Helper to format Java Instants into a native Postgres tstzrange string literal literal
     */
    private String toRangeLiteral(Instant start, Instant end) {
        return String.format("[%s,%s)", start.toString(), end.toString());
    }

    /**
     * Queries the direct time_range column using the overlap parameter
     */
    public Optional<MemoryMetadata> findBestOverlappingOverride(Instant newStart, Instant newEnd) {
        String rangeParam = toRangeLiteral(newStart, newEnd);

        // We project lower() and upper() directly so Java can read them easily as flat timestamps
        String sql = """
            SELECT lower(time_range) as start_time, upper(time_range) as end_time, metadata 
            FROM location_metadata 
            WHERE time_range && ?::tstzrange
            ORDER BY upper(time_range * ?::tstzrange) - lower(time_range * ?::tstzrange) DESC
            LIMIT 1
            """;

        try {
            MemoryMetadata match = jdbcTemplate.queryForObject(
                    sql,
                    metadataRowMapper,
                    rangeParam, rangeParam, rangeParam
            );
            return Optional.ofNullable(match);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Inserts a true native range column entry
     */
    public void insertOverride(String contextType, MemoryMetadata metadata) {
        String sql = """
            INSERT INTO location_metadata (context_type, time_range, metadata)
            VALUES (?, ?::tstzrange, ?::jsonb)
            """;
        try {
            String rangeLiteral = toRangeLiteral(metadata.getStartTime(), metadata.getEndTime());
            String jsonPayload = objectMapper.writeValueAsString(metadata.getProperties());
            jdbcTemplate.update(sql, contextType, rangeLiteral, jsonPayload);
        } catch (Exception e) {
            throw new RuntimeException("Serialization mapping failed", e);
        }
    }

    /**
     * Updates an override where the time_range is an exact match
     */
    public void updateOverridePayload(MemoryMetadata metadata) {
        String sql = """
            UPDATE location_metadata
            SET metadata = ?::jsonb
            WHERE time_range = ?::tstzrange
            """;
        try {
            String rangeLiteral = toRangeLiteral(metadata.getStartTime(), metadata.getEndTime());
            String jsonPayload = objectMapper.writeValueAsString(metadata.getProperties());
            jdbcTemplate.update(sql, jsonPayload, rangeLiteral);
        } catch (Exception e) {
            throw new RuntimeException("Serialization mapping failed", e);
        }
    }
}