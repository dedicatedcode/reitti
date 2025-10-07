package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockImageGallery;
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
public class MemoryBlockImageGalleryJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockImageGalleryJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockImageGallery> MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockImageGallery(
            rs.getLong("id"),
            rs.getLong("block_id"),
            rs.getString("image_url"),
            rs.getString("caption"),
            rs.getInt("position")
    );

    public MemoryBlockImageGallery create(MemoryBlockImageGallery image) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memory_block_image_gallery (block_id, image_url, caption, position) " +
                    "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, image.getBlockId());
            ps.setString(2, image.getImageUrl());
            ps.setString(3, image.getCaption());
            ps.setInt(4, image.getPosition());
            return ps;
        }, keyHolder);

        Long id = (Long) keyHolder.getKeys().get("id");
        return image.withId(id);
    }

    public MemoryBlockImageGallery update(MemoryBlockImageGallery image) {
        jdbcTemplate.update(
                "UPDATE memory_block_image_gallery SET caption = ?, position = ? WHERE id = ?",
                image.getCaption(),
                image.getPosition(),
                image.getId()
        );
        return image;
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery WHERE id = ?", id);
    }

    public void deleteByBlockId(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery WHERE block_id = ?", blockId);
    }

    public Optional<MemoryBlockImageGallery> findById(Long id) {
        List<MemoryBlockImageGallery> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE id = ?",
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MemoryBlockImageGallery> findByBlockId(Long blockId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_block_image_gallery WHERE block_id = ? ORDER BY position",
                MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER,
                blockId
        );
    }
}
