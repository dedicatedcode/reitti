package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockText;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockTextJdbcService {

    private static final String OWNED_BLOCK_FILTER = "EXISTS (SELECT 1 FROM memory_block mb JOIN memory m ON mb.memory_id = m.id WHERE mb.id = memory_block_text.block_id AND m.user_id = ?)";

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockTextJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockText> MEMORY_BLOCK_TEXT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockText(
            rs.getLong("block_id"),
            rs.getString("headline"),
            rs.getString("content")
    );

    public MemoryBlockText create(User user, MemoryBlockText blockText) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO memory_block_text (block_id, headline, content) " +
                        "SELECT ?, ?, ? WHERE EXISTS (SELECT 1 FROM memory_block mb JOIN memory m ON mb.memory_id = m.id WHERE mb.id = ? AND m.user_id = ?)",
                blockText.getBlockId(),
                blockText.getHeadline(),
                blockText.getContent(),
                blockText.getBlockId(),
                user.getId()
        );
        if (inserted == 0) {
            throw new IllegalStateException("Unable to create text block for block [" + blockText.getBlockId() + "]");
        }
        return blockText;
    }

    public MemoryBlockText update(User user, MemoryBlockText blockText) {
        jdbcTemplate.update(
                "UPDATE memory_block_text SET headline = ?, content = ? WHERE block_id = ? AND " + OWNED_BLOCK_FILTER,
                blockText.getHeadline(),
                blockText.getContent(),
                blockText.getBlockId(),
                user.getId()
        );
        return blockText;
    }

    public Optional<MemoryBlockText> findByBlockId(User user, Long blockId) {
        List<MemoryBlockText> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_text WHERE block_id = ? AND " + OWNED_BLOCK_FILTER,
                MEMORY_BLOCK_TEXT_ROW_MAPPER,
                blockId,
                user.getId()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(User user, Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_text WHERE block_id = ? AND " + OWNED_BLOCK_FILTER, blockId, user.getId());
    }
}
