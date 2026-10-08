package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockImageGallery;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockImageGalleryJdbcService {

    private static final String OWNED_BLOCK_FILTER = "EXISTS (SELECT 1 FROM memory_block mb JOIN memory m ON mb.memory_id = m.id WHERE mb.id = memory_block_image_gallery.block_id AND m.user_id = ?)";

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

    public MemoryBlockImageGallery create(User user, MemoryBlockImageGallery gallery) {
        try {
            String imagesJson = objectMapper.writeValueAsString(gallery.getImages());
            int inserted = jdbcTemplate.update(
                    "INSERT INTO memory_block_image_gallery (block_id, images) " +
                            "SELECT ?, ?::jsonb WHERE EXISTS (SELECT 1 FROM memory_block mb JOIN memory m ON mb.memory_id = m.id WHERE mb.id = ? AND m.user_id = ?)",
                    gallery.getBlockId(),
                    imagesJson,
                    gallery.getBlockId(),
                    user.getId()
            );
            if (inserted == 0) {
                throw new IllegalStateException("Unable to create image gallery block for block [" + gallery.getBlockId() + "]");
            }
            return gallery;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MemoryBlockImageGallery", e);
        }
    }

    public MemoryBlockImageGallery update(User user, MemoryBlockImageGallery gallery) {
        try {
            String imagesJson = objectMapper.writeValueAsString(gallery.getImages());
            jdbcTemplate.update(
                    "UPDATE memory_block_image_gallery SET images = ?::jsonb WHERE block_id = ? AND " + OWNED_BLOCK_FILTER,
                    imagesJson,
                    gallery.getBlockId(),
                    user.getId()
            );
            return gallery;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update MemoryBlockImageGallery", e);
        }
    }

    public void delete(User user, Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery WHERE block_id = ? AND " + OWNED_BLOCK_FILTER, blockId, user.getId());
    }

    public Optional<MemoryBlockImageGallery> findByBlockId(User user, Long blockId) {
        List<MemoryBlockImageGallery> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE block_id = ? AND " + OWNED_BLOCK_FILTER,
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                blockId,
                user.getId()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
