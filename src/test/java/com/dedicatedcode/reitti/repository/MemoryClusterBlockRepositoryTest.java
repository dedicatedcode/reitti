package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class MemoryClusterBlockRepositoryTest {

    @Autowired
    private MemoryClusterBlockRepository repository;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestingService testingService;
    private User user;

    @BeforeEach
    void setUp() {
         user = testingService.randomUser();

    }

    @Test
    public void testSaveAndFindByBlockId() {
        // Given
        List<Long> tripIds = List.of(1L, 2L, 3L);

        Memory memory = this.memoryJdbcService.create(user,
                new Memory("Test",
                        "Test description",
                        Instant.parse("2007-12-03T10:15:30.00Z"),
                        Instant.parse("2007-12-03T11:15:30.00Z"),
                        HeaderType.MAP,
                        null));
        MemoryBlock memoryBlock = memoryBlockJdbcService.create(new MemoryBlock(memory.getId(), BlockType.CLUSTER_TRIP, 0));
        MemoryClusterBlock cluster = new MemoryClusterBlock(memoryBlock.getId(), tripIds, "Journey to Airport", "A trip from home to the airport", BlockType.CLUSTER_TRIP);

        // When
        repository.save(user, cluster);
        Optional<MemoryClusterBlock> found = repository.findByBlockId(user, memoryBlock.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getBlockId()).isEqualTo(memoryBlock.getId());
        assertThat(found.get().getPartIds()).isEqualTo(tripIds);
        assertThat(found.get().getTitle()).isEqualTo("Journey to Airport");
        assertThat(found.get().getDescription()).isEqualTo("A trip from home to the airport");
    }

    @Test
    public void testDeleteByBlockId() {
        // Given

        Memory memory = this.memoryJdbcService.create(user,
                new Memory("Test",
                        "Test description",
                        Instant.parse("2007-12-03T10:15:30.00Z"),
                        Instant.parse("2007-12-03T11:15:30.00Z"),
                        HeaderType.MAP,
                        null));
        MemoryBlock memoryBlock = memoryBlockJdbcService.create(new MemoryBlock(memory.getId(), BlockType.CLUSTER_TRIP, 0));
        List<Long> tripIds = List.of(4L, 5L);
        MemoryClusterBlock cluster = new MemoryClusterBlock(memoryBlock.getId(), tripIds, "Another Journey", "Description", BlockType.CLUSTER_TRIP);
        repository.save(user, cluster);

        // When
        repository.deleteByBlockId(user, 101L);
        Optional<MemoryClusterBlock> found = repository.findByBlockId(user, 101L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    public void testFindByBlockIdNotFound() {
        // When
        Optional<MemoryClusterBlock> found = repository.findByBlockId(user, 999L);

        // Then
        assertThat(found).isEmpty();
    }
}
