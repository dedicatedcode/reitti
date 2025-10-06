package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockVisit;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemoryBlockVisitRepository {

    private final JdbcClient jdbcClient;

    public MemoryBlockVisitRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<MemoryBlockVisit> MEMORY_BLOCK_VISIT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockVisit(
            rs.getLong("block_id"),
            rs.getLong("visit_id")
    );

    public MemoryBlockVisit create(MemoryBlockVisit blockVisit) {
        jdbcClient.sql("""
                INSERT INTO memory_block_visit (block_id, visit_id)
                VALUES (:blockId, :visitId)
                """)
                .param("blockId", blockVisit.getBlockId())
                .param("visitId", blockVisit.getVisitId())
                .update();

        return blockVisit;
    }

    public Optional<MemoryBlockVisit> findByBlockId(Long blockId) {
        return jdbcClient.sql("SELECT * FROM memory_block_visit WHERE block_id = :blockId")
                .param("blockId", blockId)
                .query(MEMORY_BLOCK_VISIT_ROW_MAPPER)
                .optional();
    }

    public void delete(Long blockId) {
        jdbcClient.sql("DELETE FROM memory_block_visit WHERE block_id = :blockId")
                .param("blockId", blockId)
                .update();
    }
}
