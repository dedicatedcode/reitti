package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockText;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemoryBlockTextRepository {

    private final JdbcClient jdbcClient;

    public MemoryBlockTextRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<MemoryBlockText> MEMORY_BLOCK_TEXT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockText(
            rs.getLong("block_id"),
            rs.getString("headline"),
            rs.getString("content")
    );

    public MemoryBlockText create(MemoryBlockText blockText) {
        jdbcClient.sql("""
                INSERT INTO memory_block_text (block_id, headline, content)
                VALUES (:blockId, :headline, :content)
                """)
                .param("blockId", blockText.getBlockId())
                .param("headline", blockText.getHeadline())
                .param("content", blockText.getContent())
                .update();

        return blockText;
    }

    public MemoryBlockText update(MemoryBlockText blockText) {
        jdbcClient.sql("""
                UPDATE memory_block_text
                SET headline = :headline,
                    content = :content
                WHERE block_id = :blockId
                """)
                .param("blockId", blockText.getBlockId())
                .param("headline", blockText.getHeadline())
                .param("content", blockText.getContent())
                .update();

        return blockText;
    }

    public Optional<MemoryBlockText> findByBlockId(Long blockId) {
        return jdbcClient.sql("SELECT * FROM memory_block_text WHERE block_id = :blockId")
                .param("blockId", blockId)
                .query(MEMORY_BLOCK_TEXT_ROW_MAPPER)
                .optional();
    }

    public void delete(Long blockId) {
        jdbcClient.sql("DELETE FROM memory_block_text WHERE block_id = :blockId")
                .param("blockId", blockId)
                .update();
    }
}
