package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockVisit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockVisit> MEMORY_BLOCK_VISIT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockVisit(
            rs.getLong("block_id"),
            rs.getLong("visit_id")
    );

    public MemoryBlockVisit create(MemoryBlockVisit blockVisit) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_visit (block_id, visit_id) VALUES (?, ?)",
                blockVisit.getBlockId(),
                blockVisit.getProcessedVisitId()
        );
        return blockVisit;
    }

    public Optional<MemoryBlockVisit> findByBlockId(Long blockId) {
        List<MemoryBlockVisit> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_visit WHERE block_id = ?",
                MEMORY_BLOCK_VISIT_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_visit WHERE block_id = ?", blockId);
    }
}
