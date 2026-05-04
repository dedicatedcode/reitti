package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.ApiTokenUsage;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class ApiTokenJdbcServiceTest {

    @Autowired
    private ApiTokenJdbcService apiTokenJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void create_ShouldPersistTokenAndAssignId() {
        ApiToken token = testingService.createApiToken(testUser, "Integration Test Token", null);

        assertNotNull(token.getId());
        assertNotNull(token.getToken());
        assertEquals("Integration Test Token", token.getName());
        assertNull(token.getDevice());
        assertNotNull(token.getCreatedAt());
        assertNull(token.getLastUsedAt());
        assertEquals(testUser.getId(), token.getUser().getId());
    }

    @Test
    void findByToken_WithExistingToken_ShouldReturnToken() {
        ApiToken token = testingService.createApiToken(testUser, "find-by-token-test", null);

        Optional<ApiToken> found = apiTokenJdbcService.findByToken(token.getToken());
        assertTrue(found.isPresent());
        assertEquals(token.getId(), found.get().getId());
        assertEquals(token.getToken(), found.get().getToken());
    }

    @Test
    void findByToken_WithNonExistingToken_ShouldReturnEmpty() {
        Optional<ApiToken> found = apiTokenJdbcService.findByToken("non-existing-token-12345");
        assertTrue(found.isEmpty());
    }

    @Test
    void findById_WithExistingId_ShouldReturnToken() {
        ApiToken token = testingService.createApiToken(testUser, "find-by-id-test", null);

        Optional<ApiToken> found = apiTokenJdbcService.findById(token.getId());
        assertTrue(found.isPresent());
        assertEquals(token.getId(), found.get().getId());
    }

    @Test
    void findById_WithNonExistingId_ShouldReturnEmpty() {
        Optional<ApiToken> found = apiTokenJdbcService.findById(999999L);
        assertTrue(found.isEmpty());
    }

    @Test
    void findByUser_ShouldReturnTokensForGivenUser() {
        User anotherUser = testingService.randomUser();

        ApiToken token1 = testingService.createApiToken(testUser, "Token A", null);
        ApiToken token2 = testingService.createApiToken(testUser, "Token B", null);
        testingService.createApiToken(anotherUser, "Token C", null);

        List<ApiToken> tokens = apiTokenJdbcService.findByUser(testUser);
        assertEquals(2, tokens.size());
        assertTrue(tokens.stream().allMatch(t -> t.getUser().getId().equals(testUser.getId())));
        assertTrue(tokens.stream().anyMatch(t -> t.getId().equals(token1.getId())));
        assertTrue(tokens.stream().anyMatch(t -> t.getId().equals(token2.getId())));
    }

    @Test
    void findByUser_WithDevice_ShouldIncludeDeviceInfo() {
        Device device = createTestDevice();
        ApiToken token = testingService.createApiToken(testUser, "Token with device", device);

        List<ApiToken> tokens = apiTokenJdbcService.findByUser(testUser);
        Optional<ApiToken> found = tokens.stream().filter(t -> t.getId().equals(token.getId())).findFirst();
        assertTrue(found.isPresent());
        ApiToken retrieved = found.get();
        assertNotNull(retrieved.getDevice());
        assertEquals(device.getId(), retrieved.getDevice().id());
    }

    @Test
    void update_ShouldModifyTokenName() {
        ApiToken original = testingService.createApiToken(testUser, "Original Name", null);
        ApiToken updated = new ApiToken(
                original.getId(),
                original.getToken(),
                testUser,
                original.getDevice(),
                "Updated Name",
                original.getCreatedAt(),
                Instant.now()
        );

        ApiToken result = apiTokenJdbcService.save(updated);
        assertEquals("Updated Name", result.getName());
        assertNotNull(result.getLastUsedAt());

        Optional<ApiToken> persisted = apiTokenJdbcService.findById(result.getId());
        assertTrue(persisted.isPresent());
        assertEquals("Updated Name", persisted.get().getName());
    }

    @Test
    void delete_ShouldRemoveToken() {
        ApiToken token = testingService.createApiToken(testUser, "To delete", null);

        apiTokenJdbcService.delete(token);
        Optional<ApiToken> found = apiTokenJdbcService.findById(token.getId());
        assertTrue(found.isEmpty());
    }

    @Test
    void deleteById_ShouldThrowWhenTokenNotFound() {
        assertThrows(org.springframework.dao.EmptyResultDataAccessException.class,
                () -> apiTokenJdbcService.deleteById(999999L));
    }

    @Test
    void count_ShouldReturnCurrentTokenCount() {
        long before = apiTokenJdbcService.count();
        testingService.createApiToken(testUser, "Count token 1", null);
        testingService.createApiToken(testUser, "Count token 2", null);

        long after = apiTokenJdbcService.count();
        assertEquals(before + 2, after);
    }

    @Test
    void deleteForUser_ShouldDeleteOnlyTokensOfSpecifiedUser() {
        User anotherUser = testingService.randomUser();

        testingService.createApiToken(testUser, "T1", null);
        testingService.createApiToken(testUser, "T2", null);
        testingService.createApiToken(anotherUser, "T3", null);

        apiTokenJdbcService.deleteForUser(testUser);

        assertTrue(apiTokenJdbcService.findByUser(testUser).isEmpty());
        assertEquals(1, apiTokenJdbcService.findByUser(anotherUser).size());
    }

    @Test
    void trackUsage_AndGetUsages_ShouldRecordUsage() {
        ApiToken token = testingService.createApiToken(testUser, "Usage token", null);

        apiTokenJdbcService.trackUsage(token.getToken(), "/api/test-endpoint", "127.0.0.1");

        List<ApiTokenUsage> usages = apiTokenJdbcService.getUsages(testUser, 10);
        assertTrue(usages.size() > 0);
        ApiTokenUsage usage = usages.get(0);
        assertEquals("/api/test-endpoint", usage.endpoint());
        assertEquals("127.0.0.1", usage.ip());
        assertNotNull(usage.at());
    }

    @Test
    void getUsages_WithNoUsages_ShouldReturnEmptyList() {
        List<ApiTokenUsage> usages = apiTokenJdbcService.getUsages(testUser, 10);
        assertTrue(usages.isEmpty());
    }

    @Test
    void getUsages_ShouldRespectMaxRows() {
        ApiToken token = testingService.createApiToken(testUser, "Max rows token", null);
        apiTokenJdbcService.trackUsage(token.getToken(), "/a", "1.2.3.4");
        apiTokenJdbcService.trackUsage(token.getToken(), "/b", "1.2.3.5");
        apiTokenJdbcService.trackUsage(token.getToken(), "/c", "1.2.3.6");

        List<ApiTokenUsage> usages = apiTokenJdbcService.getUsages(testUser, 2);
        assertEquals(2, usages.size());
    }

    private Device createTestDevice() {
        String name = "test-device-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO devices (name, enabled, show_on_map, color, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?)",
                name, true, false, "#ffaa00", Timestamp.from(now), Timestamp.from(now), 0L
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM devices WHERE name = ?", Long.class, name);
        return new Device(id, name, true, false, "#ffaa00", now, now, 0L);
    }
}
