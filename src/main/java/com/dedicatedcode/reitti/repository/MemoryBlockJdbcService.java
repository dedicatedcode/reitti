package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.*;
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

    private static final RowMapper<MemoryBlockText> MEMORY_BLOCK_TEXT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockText(
            rs.getLong("block_id"),
            rs.getString("headline"),
            rs.getString("content")
    );

    private static final RowMapper<MemoryBlockVisit> MEMORY_BLOCK_VISIT_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockVisit(
            rs.getLong("block_id"),
            rs.getLong("processed_visit_id")
    );

    private static final RowMapper<MemoryBlockTrip> MEMORY_BLOCK_TRIP_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockTrip(
            rs.getLong("block_id"),
            rs.getLong("trip_id")
    );

    private static final RowMapper<MemoryBlockImageGallery> MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockImageGallery(
            rs.getLong("id"),
            rs.getLong("block_id"),
            rs.getString("image_url"),
            rs.getString("caption"),
            rs.getInt("position")
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

        Long id = (Long) keyHolder.getKeys().get("id");
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

    public void deleteByMemoryId(Long memoryId) {
        jdbcTemplate.update("DELETE FROM memory_block WHERE memory_id = ?", memoryId);
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

    // Text Block methods
    public MemoryBlockText createTextBlock(Long blockId, MemoryBlockText textBlock) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_text (block_id, headline, content) VALUES (?, ?, ?)",
                blockId,
                textBlock.getHeadline(),
                textBlock.getContent()
        );
        return new MemoryBlockText(blockId, textBlock.getHeadline(), textBlock.getContent());
    }

    public Optional<MemoryBlockText> findTextBlock(Long blockId) {
        List<MemoryBlockText> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_text WHERE block_id = ?",
                MEMORY_BLOCK_TEXT_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // Visit Block methods
    public MemoryBlockVisit createVisitBlock(Long blockId, MemoryBlockVisit visitBlock) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_visit (block_id, processed_visit_id) VALUES (?, ?)",
                blockId,
                visitBlock.getProcessedVisitId()
        );
        return new MemoryBlockVisit(blockId, visitBlock.getProcessedVisitId());
    }

    public Optional<MemoryBlockVisit> findVisitBlock(Long blockId) {
        List<MemoryBlockVisit> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_visit WHERE block_id = ?",
                MEMORY_BLOCK_VISIT_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // Trip Block methods
    public MemoryBlockTrip createTripBlock(Long blockId, MemoryBlockTrip tripBlock) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_trip (block_id, trip_id) VALUES (?, ?)",
                blockId,
                tripBlock.getTripId()
        );
        return new MemoryBlockTrip(blockId, tripBlock.getTripId());
    }

    public Optional<MemoryBlockTrip> findTripBlock(Long blockId) {
        List<MemoryBlockTrip> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_trip WHERE block_id = ?",
                MEMORY_BLOCK_TRIP_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // Image Gallery Block methods
    public MemoryBlockImageGallery createImageGalleryBlock(Long blockId, MemoryBlockImageGallery imageBlock) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memory_block_image_gallery (block_id, image_url, caption, position) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, blockId);
            ps.setString(2, imageBlock.getImageUrl());
            ps.setString(3, imageBlock.getCaption());
            ps.setInt(4, imageBlock.getPosition());
            return ps;
        }, keyHolder);

        Long id = (Long) keyHolder.getKeys().get("id");
        return imageBlock.withId(id);
    }

    public List<MemoryBlockImageGallery> findImageGalleryBlocks(Long blockId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE block_id = ? ORDER BY position",
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                blockId
        );
    }
}
