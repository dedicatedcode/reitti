package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlock> MEMORY_BLOCK_ROW_MAPPER = (rs, rowNum) -> new MemoryBlock(
            rs.getLong("id"),
            rs.getLong("memory_id"),
            BlockType.valueOf(rs.getString("block_type")),
            rs.getInt("position"),
            rs.getLong("version")
    );

    public MemoryBlock create(User user, MemoryBlock block) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memory_block (memory_id, block_type, position, version) " +
                    "SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM memory WHERE id = ? AND user_id = ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, block.getMemoryId());
            ps.setString(2, block.getBlockType().name());
            ps.setInt(3, block.getPosition());
            ps.setLong(4, block.getVersion());
            ps.setLong(5, block.getMemoryId());
            ps.setLong(6, user.getId());
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        Long id = keys != null ? (Long) keys.get("id") : null;
        if (id == null) {
            throw new IllegalStateException("Unable to create block for memory [" + block.getMemoryId() + "]");
        }
        return block.withId(id);
    }

    public MemoryBlock update(User user, MemoryBlock block) {
        int updated = jdbcTemplate.update(
                "UPDATE memory_block " +
                "SET position = ?, version = version + 1 " +
                "WHERE id = ? AND version = ? " +
                "AND EXISTS (SELECT 1 FROM memory WHERE id = memory_block.memory_id AND user_id = ?)",
                block.getPosition(),
                block.getId(),
                block.getVersion(),
                user.getId()
        );

        if (updated == 0) {
            throw new IllegalStateException("Memory block not found or version mismatch");
        }

        return block.withVersion(block.getVersion() + 1);
    }

    public void delete(User user, Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block WHERE id = ? " +
                "AND EXISTS (SELECT 1 FROM memory WHERE id = memory_block.memory_id AND user_id = ?)", blockId, user.getId());
    }

    public Optional<MemoryBlock> findById(User user, Long id) {
        List<MemoryBlock> results = jdbcTemplate.query(
                "SELECT * FROM memory_block LEFT JOIN memory ON memory_block.memory_id = memory.id WHERE memory_block.id = ? AND memory.user_id = ?",
                MEMORY_BLOCK_ROW_MAPPER,
                id,
                user.getId()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MemoryBlock> findByMemoryId(User user, Long memoryId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_block WHERE memory_id = ? " +
                "AND EXISTS (SELECT 1 FROM memory WHERE id = memory_block.memory_id AND user_id = ?) ORDER BY position",
                MEMORY_BLOCK_ROW_MAPPER,
                memoryId,
                user.getId()
        );
    }

    public int getMaxPosition(User user, Long memoryId) {
        Integer maxPosition = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(position), -1) FROM memory_block WHERE memory_id = ? " +
                "AND EXISTS (SELECT 1 FROM memory WHERE id = memory_block.memory_id AND user_id = ?)",
                Integer.class,
                memoryId,
                user.getId()
        );
        return maxPosition != null ? maxPosition : -1;
    }


    public void deleteByMemoryId(User user, Long memoryId) {
        this.jdbcTemplate.update("DELETE FROM memory_block WHERE memory_id = ? " +
                "AND EXISTS (SELECT 1 FROM memory WHERE id = memory_block.memory_id AND user_id = ?)", memoryId, user.getId());
    }
}
