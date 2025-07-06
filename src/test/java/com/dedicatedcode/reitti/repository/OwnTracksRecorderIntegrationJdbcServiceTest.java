package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@IntegrationTest
class OwnTracksRecorderIntegrationJdbcServiceTest {

    @Autowired
    private OwnTracksRecorderIntegrationJdbcService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = createTestUser("testuser", "password", "Test User");
    }

    @Test
    void findByUser_WhenNoIntegrationExists_ReturnsEmpty() {
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(testUser);
        
        assertThat(result).isEmpty();
    }

    @Test
    void save_WhenNewIntegration_InsertsSuccessfully() {
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );

        OwnTracksRecorderIntegration saved = service.save(testUser, integration);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBaseUrl()).isEqualTo("http://localhost:8083");
        assertThat(saved.getUsername()).isEqualTo("testuser");
        assertThat(saved.getDeviceId()).isEqualTo("device123");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getLastSuccessfulFetch()).isNull();
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void save_WithLastSuccessfulFetch_SavesTimestamp() {
        Instant now = Instant.now();
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                null,
                "http://localhost:8083",
                "testuser",
                "device123",
                true,
                now,
                null
        );

        OwnTracksRecorderIntegration saved = service.save(testUser, integration);

        assertThat(saved.getLastSuccessfulFetch()).isEqualTo(now);
    }

    @Test
    void findByUser_WhenIntegrationExists_ReturnsIntegration() {
        // First save an integration
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );
        service.save(testUser, integration);

        // Then find it
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(testUser);

        assertThat(result).isPresent();
        assertThat(result.get().getBaseUrl()).isEqualTo("http://localhost:8083");
        assertThat(result.get().getUsername()).isEqualTo("testuser");
        assertThat(result.get().getDeviceId()).isEqualTo("device123");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void update_WhenIntegrationExists_UpdatesSuccessfully() {
        // First save an integration
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );
        OwnTracksRecorderIntegration saved = service.save(testUser, integration);

        // Update it
        OwnTracksRecorderIntegration updated = new OwnTracksRecorderIntegration(
                saved.getId(),
                "http://localhost:8084",
                "newuser",
                "device456",
                false,
                Instant.now(),
                saved.getVersion()
        );

        OwnTracksRecorderIntegration result = service.update(updated);

        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getBaseUrl()).isEqualTo("http://localhost:8084");
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getDeviceId()).isEqualTo("device456");
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getLastSuccessfulFetch()).isNotNull();
        assertThat(result.getVersion()).isEqualTo(2L);
    }

    @Test
    void update_WithWrongVersion_ThrowsException() {
        // First save an integration
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );
        OwnTracksRecorderIntegration saved = service.save(testUser, integration);

        // Try to update with wrong version
        OwnTracksRecorderIntegration updated = new OwnTracksRecorderIntegration(
                saved.getId(),
                "http://localhost:8084",
                "newuser",
                "device456",
                false,
                null,
                999L // Wrong version
        );

        assertThatThrownBy(() -> service.update(updated))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Optimistic locking failure");
    }

    @Test
    void delete_WhenIntegrationExists_DeletesSuccessfully() {
        // First save an integration
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );
        OwnTracksRecorderIntegration saved = service.save(testUser, integration);

        // Delete it
        service.delete(saved);

        // Verify it's gone
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(testUser);
        assertThat(result).isEmpty();
    }

    @Test
    void findByUser_WithDifferentUser_ReturnsEmpty() {
        // Save integration for first user
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true
        );
        service.save(testUser, integration);

        // Create second user
        User otherUser = createTestUser("otheruser", "password", "Other User");

        // Try to find integration for second user
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(otherUser);
        assertThat(result).isEmpty();
    }

    private User createTestUser(String username, String password, String displayName) {
        // Insert user directly into database for testing
        String sql = "INSERT INTO users (username, password, display_name, version) VALUES (?, ?, ?, ?) RETURNING id";
        Long userId = jdbcTemplate.queryForObject(sql, Long.class, username, password, displayName, 1L);
        
        return new User(userId, username, password, displayName, 1L);
    }
}
