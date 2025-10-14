package com.dedicatedcode.reitti.model.memory.MemoryClusterBlock;
import com.dedicatedcode.reitti.repository.MemoryClusterBlockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test") // Assumes a test profile with H2 in-memory DB
@Transactional
public class MemoryClusterBlockRepositoryTest {

    @Autowired
    private MemoryClusterBlockRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testSaveAndFindByBlockId() {
        // Given
        List<Long> tripIds = List.of(1L, 2L, 3L);
        MemoryClusterBlock cluster = new MemoryClusterBlock(100L, tripIds, "Journey to Airport", "A trip from home to the airport");

        // When
        repository.save(cluster);
        Optional<MemoryClusterBlock> found = repository.findByBlockId(100L);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getBlockId()).isEqualTo(100L);
        assertThat(found.get().getTripIds()).isEqualTo(tripIds);
        assertThat(found.get().getTitle()).isEqualTo("Journey to Airport");
        assertThat(found.get().getDescription()).isEqualTo("A trip from home to the airport");
    }

    @Test
    public void testDeleteByBlockId() {
        // Given
        List<Long> tripIds = List.of(4L, 5L);
        MemoryClusterBlock cluster = new MemoryClusterBlock(101L, tripIds, "Another Journey", "Description");
        repository.save(cluster);

        // When
        repository.deleteByBlockId(101L);
        Optional<MemoryClusterBlock> found = repository.findByBlockId(101L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    public void testFindByBlockIdNotFound() {
        // When
        Optional<MemoryClusterBlock> found = repository.findByBlockId(999L);

        // Then
        assertThat(found).isEmpty();
    }
}
