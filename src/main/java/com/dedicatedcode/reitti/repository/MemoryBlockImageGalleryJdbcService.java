package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockImageGallery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockImageGalleryJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MemoryBlockImageGalleryJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<MemoryBlockImageGallery> MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER = new RowMapper<>() {
        @Override
        public MemoryBlockImageGallery mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long blockId = rs.getLong("block_id");
            String imagesJson = rs.getString("images");
            List<MemoryBlockImageGallery.GalleryImage> images = null;
            try {
                images = objectMapper.readValue(imagesJson, new TypeReference<List<MemoryBlockImageGallery.GalleryImage>>() {});
            } catch (Exception e) {
                throw new SQLException("Failed to parse images JSON", e);
            }
            return new MemoryBlockImageGallery(blockId, images);
        }
    };

    public MemoryBlockImageGallery create(MemoryBlockImageGallery gallery) {
        try {
            String imagesJson = objectMapper.writeValueAsString(gallery.getImages());
            jdbcTemplate.update(
                    "INSERT INTO memory_block_image_gallery (block_id, images) VALUES (?, ?::jsonb)",
                    gallery.getBlockId(),
                    imagesJson
            );
            return gallery;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MemoryBlockImageGallery", e);
        }
    }

    public MemoryBlockImageGallery update(MemoryBlockImageGallery gallery) {
        try {
            String imagesJson = objectMapper.writeValueAsString(gallery.getImages());
            jdbcTemplate.update(
                    "UPDATE memory_block_image_gallery SET images = ?::jsonb WHERE block_id = ?",
                    imagesJson,
                    gallery.getBlockId()
            );
            return gallery;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update MemoryBlockImageGallery", e);
        }
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery WHERE block_id = ?", blockId);
    }

    public void deleteByBlockId(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery WHERE block_id = ?", blockId);
    }

    public Optional<MemoryBlockImageGallery> findById(Long blockId) {
        List<MemoryBlockImageGallery> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE block_id = ?",
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MemoryBlockImageGallery> findByBlockId(Long blockId) {
        List<MemoryBlockImageGallery> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE block_id = ?",
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                blockId
        );
        return results;
    }
}
