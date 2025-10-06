package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryRepository {

    private final JdbcClient jdbcClient;

    public MemoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<Memory> MEMORY_ROW_MAPPER = (rs, rowNum) -> new Memory(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class),
            HeaderType.valueOf(rs.getString("header_type")),
            rs.getString("header_image_url"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    public Memory create(User user, Memory memory) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcClient.sql("""
                INSERT INTO memory (user_id, title, description, start_date, end_date, header_type, header_image_url, created_at, updated_at, version)
                VALUES (:userId, :title, :description, :startDate, :endDate, :headerType, :headerImageUrl, :createdAt, :updatedAt, :version)
                """)
                .param("userId", user.getId())
                .param("title", memory.getTitle())
                .param("description", memory.getDescription())
                .param("startDate", memory.getStartDate())
                .param("endDate", memory.getEndDate())
                .param("headerType", memory.getHeaderType().name())
                .param("headerImageUrl", memory.getHeaderImageUrl())
                .param("createdAt", memory.getCreatedAt())
                .param("updatedAt", memory.getUpdatedAt())
                .param("version", memory.getVersion())
                .update(keyHolder);

        Long id = keyHolder.getKeyAs(Long.class);
        return memory.withId(id);
    }

    public Memory update(User user, Memory memory) {
        int updated = jdbcClient.sql("""
                UPDATE memory
                SET title = :title,
                    description = :description,
                    start_date = :startDate,
                    end_date = :endDate,
                    header_type = :headerType,
                    header_image_url = :headerImageUrl,
                    updated_at = :updatedAt,
                    version = version + 1
                WHERE id = :id AND user_id = :userId AND version = :version
                """)
                .param("id", memory.getId())
                .param("userId", user.getId())
                .param("title", memory.getTitle())
                .param("description", memory.getDescription())
                .param("startDate", memory.getStartDate())
                .param("endDate", memory.getEndDate())
                .param("headerType", memory.getHeaderType().name())
                .param("headerImageUrl", memory.getHeaderImageUrl())
                .param("updatedAt", memory.getUpdatedAt())
                .param("version", memory.getVersion())
                .update();

        if (updated == 0) {
            throw new IllegalStateException("Memory not found or version mismatch");
        }

        return memory.withVersion(memory.getVersion() + 1);
    }

    public void delete(User user, Long memoryId) {
        jdbcClient.sql("DELETE FROM memory WHERE id = :id AND user_id = :userId")
                .param("id", memoryId)
                .param("userId", user.getId())
                .update();
    }

    public Optional<Memory> findById(User user, Long id) {
        return jdbcClient.sql("SELECT * FROM memory WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", user.getId())
                .query(MEMORY_ROW_MAPPER)
                .optional();
    }

    public List<Memory> findAllByUser(User user) {
        return jdbcClient.sql("SELECT * FROM memory WHERE user_id = :userId ORDER BY created_at DESC")
                .param("userId", user.getId())
                .query(MEMORY_ROW_MAPPER)
                .list();
    }

    public List<Memory> findByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return jdbcClient.sql("""
                SELECT * FROM memory 
                WHERE user_id = :userId 
                AND (start_date <= :endDate AND end_date >= :startDate)
                ORDER BY start_date DESC
                """)
                .param("userId", user.getId())
                .param("startDate", startDate)
                .param("endDate", endDate)
                .query(MEMORY_ROW_MAPPER)
                .list();
    }
}
