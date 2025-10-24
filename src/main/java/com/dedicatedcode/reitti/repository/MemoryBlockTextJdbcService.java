package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockTextJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockTextJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockText> MEMORY_BLOCK_TEXT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockText(
            rs.getLong("block_id"),
            rs.getString("headline"),
            rs.getString("content")
    );

    public MemoryBlockText create(MemoryBlockText blockText) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_text (block_id, headline, content) VALUES (?, ?, ?)",
                blockText.getBlockId(),
                blockText.getHeadline(),
                blockText.getContent()
        );
        return blockText;
    }

    public MemoryBlockText update(MemoryBlockText blockText) {
        jdbcTemplate.update(
                "UPDATE memory_block_text SET headline = ?, content = ? WHERE block_id = ?",
                blockText.getHeadline(),
                blockText.getContent(),
                blockText.getBlockId()
        );
        return blockText;
    }

    public Optional<MemoryBlockText> findByBlockId(Long blockId) {
        List<MemoryBlockText> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_text WHERE block_id = ?",
                MEMORY_BLOCK_TEXT_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_text WHERE block_id = ?", blockId);
    }
}
