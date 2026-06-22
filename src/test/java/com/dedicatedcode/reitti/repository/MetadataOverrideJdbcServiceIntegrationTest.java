package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MetadataOverrideJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MetadataOverrideJdbcServiceIntegrationTest {

    @Autowired
    private MetadataOverrideJdbcService overrideJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndRetrieveOverrideByUser() {
        User user = testingService.randomUser();
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant end = start.plusSeconds(3600);

        MemoryMetadata metadata = new MemoryMetadata(start, end);
        metadata.setReason("test");

        overrideJdbcService.insertOverride(user, "TRIP", metadata);

        // verify DB row exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM location_metadata WHERE user_id = ?", Integer.class, user.getId());
        assertEquals(1, count);

        Optional<MemoryMetadata> found = overrideJdbcService.findBestOverlappingOverride(user, start, end);
        assertTrue(found.isPresent());
        assertEquals("test", found.get().getReason());
    }

    @Test
    void overlappingOverrideReturnsLongestOverlap() {
        User user = testingService.randomUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Create two overlapping entries: one with a short range, one with a long range
        MemoryMetadata shortMeta = new MemoryMetadata(now.minusSeconds(600), now.plusSeconds(600));
        shortMeta.setReason("short");
        overrideJdbcService.insertOverride(user, "VISIT", shortMeta);

        MemoryMetadata longMeta = new MemoryMetadata(now.minusSeconds(3600), now.plusSeconds(3600));
        longMeta.setReason("long");
        overrideJdbcService.insertOverride(user, "VISIT", longMeta);

        // Query a range that overlaps both; should return the one with the greatest overlap (the long one)
        Optional<MemoryMetadata> best = overrideJdbcService.findBestOverlappingOverride(
            user, now.minusSeconds(1200), now.plusSeconds(1200));
        assertTrue(best.isPresent());
        assertEquals("long", best.get().getReason());
    }

    @Test
    void userIsolation() {
        User userA = testingService.randomUser();
        User userB = testingService.randomUser();
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plusSeconds(3600);

        MemoryMetadata meta = new MemoryMetadata(start, end);
        meta.setReason("userA data");
        overrideJdbcService.insertOverride(userA, "TRIP", meta);

        // user B should not see user A's override
        assertTrue(overrideJdbcService.findBestOverlappingOverride(userB, start, end).isEmpty());
    }

    @Test
    void updateOverridePayload() {
        User user = testingService.randomUser();
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plusSeconds(3600);

        MemoryMetadata meta = new MemoryMetadata(start, end);
        meta.setDescription("original");
        overrideJdbcService.insertOverride(user, "TRIP", meta);

        meta.setDescription("updated");
        overrideJdbcService.updateOverridePayload(user, meta);

        Optional<MemoryMetadata> updated = overrideJdbcService.findBestOverlappingOverride(user, start, end);
        assertTrue(updated.isPresent());
        assertEquals("updated", updated.get().getDescription());
    }

    @Test
    void findDistinctReasonSuggestions() {
        User user = testingService.randomUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        insertMeta(user, now, now.plusSeconds(100), Map.of("reason", "groceries"));
        insertMeta(user, now, now.plusSeconds(100), Map.of("reason", "grocery shopping"));
        insertMeta(user, now, now.plusSeconds(100), Map.of("reason", "gardening"));

        List<String> suggestions = overrideJdbcService.findDistinctSuggestions(user, "reason", "gr");
        assertEquals(2, suggestions.size());
        assertTrue(suggestions.contains("groceries"));
        assertTrue(suggestions.contains("grocery shopping"));
    }

    @Test
    void findDistinctTagSuggestions() {
        User user = testingService.randomUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        insertMeta(user, now, now.plusSeconds(100), Map.of("tags", List.of("java", "spring")));
        insertMeta(user, now, now.plusSeconds(100), Map.of("tags", List.of("javascript", "react")));
        insertMeta(user, now, now.plusSeconds(100), Map.of("tags", List.of("spring-boot")));

        List<String> suggestions = overrideJdbcService.findDistinctSuggestions(user, "tags", "java");
        assertEquals(2, suggestions.size());
        assertTrue(suggestions.contains("java"));
        assertTrue(suggestions.contains("javascript"));
    }

    @Test
    void tagSuggestionsRespectUserBoundary() {
        User userA = testingService.randomUser();
        User userB = testingService.randomUser();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        insertMeta(userA, now, now.plusSeconds(100), Map.of("tags", List.of("private")));
        assertTrue(overrideJdbcService.findDistinctSuggestions(userB, "tags", "p").isEmpty());
    }

    private void insertMeta(User user, Instant start, Instant end, Map<String, Object> props) {
        MemoryMetadata meta = new MemoryMetadata(start, end);
        meta.setProperties(props);
        overrideJdbcService.insertOverride(user, "TRIP", meta);
    }
}