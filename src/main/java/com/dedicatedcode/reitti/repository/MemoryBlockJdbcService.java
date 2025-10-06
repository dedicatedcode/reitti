package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
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

    public MemoryBlock create(MemoryBlock block) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memory_block (memory_id, block_type, position, version) " +
                    "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, block.getMemoryId());
            ps.setString(2, block.getBlockType().name());
            ps.setInt(3, block.getPosition());
            ps.setLong(4, block.getVersion());
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKeyAs(Long.class);
        return block.withId(id);
    }

    public MemoryBlock update(MemoryBlock block) {
        int updated = jdbcTemplate.update(
                "UPDATE memory_block " +
                "SET position = ?, version = version + 1 " +
                "WHERE id = ? AND version = ?",
                block.getPosition(),
                block.getId(),
                block.getVersion()
        );

        if (updated == 0) {
            throw new IllegalStateException("Memory block not found or version mismatch");
        }

        return block.withVersion(block.getVersion() + 1);
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block WHERE id = ?", blockId);
    }

    public Optional<MemoryBlock> findById(Long id) {
        List<MemoryBlock> results = jdbcTemplate.query(
                "SELECT * FROM memory_block WHERE id = ?",
                MEMORY_BLOCK_ROW_MAPPER,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MemoryBlock> findByMemoryId(Long memoryId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_block WHERE memory_id = ? ORDER BY position",
                MEMORY_BLOCK_ROW_MAPPER,
                memoryId
        );
    }

    public int getMaxPosition(Long memoryId) {
        Integer maxPosition = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(position), -1) FROM memory_block WHERE memory_id = ?",
                Integer.class,
                memoryId
        );
        return maxPosition != null ? maxPosition : -1;
    }
}
