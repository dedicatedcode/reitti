package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockRepository {

    private final JdbcClient jdbcClient;

    public MemoryBlockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
        
        jdbcClient.sql("""
                INSERT INTO memory_block (memory_id, block_type, position, version)
                VALUES (:memoryId, :blockType, :position, :version)
                """)
                .param("memoryId", block.getMemoryId())
                .param("blockType", block.getBlockType().name())
                .param("position", block.getPosition())
                .param("version", block.getVersion())
                .update(keyHolder);

        Long id = keyHolder.getKeyAs(Long.class);
        return block.withId(id);
    }

    public MemoryBlock update(MemoryBlock block) {
        int updated = jdbcClient.sql("""
                UPDATE memory_block
                SET position = :position,
                    version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", block.getId())
                .param("position", block.getPosition())
                .param("version", block.getVersion())
                .update();

        if (updated == 0) {
            throw new IllegalStateException("Memory block not found or version mismatch");
        }

        return block.withVersion(block.getVersion() + 1);
    }

    public void delete(Long blockId) {
        jdbcClient.sql("DELETE FROM memory_block WHERE id = :id")
                .param("id", blockId)
                .update();
    }

    public Optional<MemoryBlock> findById(Long id) {
        return jdbcClient.sql("SELECT * FROM memory_block WHERE id = :id")
                .param("id", id)
                .query(MEMORY_BLOCK_ROW_MAPPER)
                .optional();
    }

    public List<MemoryBlock> findByMemoryId(Long memoryId) {
        return jdbcClient.sql("SELECT * FROM memory_block WHERE memory_id = :memoryId ORDER BY position")
                .param("memoryId", memoryId)
                .query(MEMORY_BLOCK_ROW_MAPPER)
                .list();
    }

    public int getMaxPosition(Long memoryId) {
        Integer maxPosition = jdbcClient.sql("SELECT COALESCE(MAX(position), -1) FROM memory_block WHERE memory_id = :memoryId")
                .param("memoryId", memoryId)
                .query(Integer.class)
                .single();
        return maxPosition != null ? maxPosition : -1;
    }
}
