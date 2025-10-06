package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockImageGallery;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockImageGalleryRepository {

    private final JdbcClient jdbcClient;

    public MemoryBlockImageGalleryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
        
        jdbcClient.sql("""
                INSERT INTO memory_block_image_gallery (block_id, image_url, caption, position)
                VALUES (:blockId, :imageUrl, :caption, :position)
                """)
                .param("blockId", image.getBlockId())
                .param("imageUrl", image.getImageUrl())
                .param("caption", image.getCaption())
                .param("position", image.getPosition())
                .update(keyHolder);

        Long id = keyHolder.getKeyAs(Long.class);
        return image.withId(id);
    }

    public MemoryBlockImageGallery update(MemoryBlockImageGallery image) {
        jdbcClient.sql("""
                UPDATE memory_block_image_gallery
                SET caption = :caption,
                    position = :position
                WHERE id = :id
                """)
                .param("id", image.getId())
                .param("caption", image.getCaption())
                .param("position", image.getPosition())
                .update();

        return image;
    }

    public void delete(Long id) {
        jdbcClient.sql("DELETE FROM memory_block_image_gallery WHERE id = :id")
                .param("id", id)
                .update();
    }

    public void deleteByBlockId(Long blockId) {
        jdbcClient.sql("DELETE FROM memory_block_image_gallery WHERE block_id = :blockId")
                .param("blockId", blockId)
                .update();
    }

    public Optional<MemoryBlockImageGallery> findById(Long id) {
        return jdbcClient.sql("SELECT * FROM memory_block_image_gallery WHERE id = :id")
                .param("id", id)
                .query(MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER)
                .optional();
    }

    public List<MemoryBlockImageGallery> findByBlockId(Long blockId) {
        return jdbcClient.sql("SELECT * FROM memory_block_image_gallery WHERE block_id = :blockId ORDER BY position")
                .param("blockId", blockId)
                .query(MEMORY_BLOCK_IMAGE_GALLERY_ROW_MAPPER)
                .list();
    }
}
