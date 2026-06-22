package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class OwnTracksRecorderIntegrationJdbcServiceTest {

    @Autowired
    private OwnTracksRecorderIntegrationJdbcService service;

    @Autowired
    private TestingService testingService;

    private User admin;
    private Device device;

    @BeforeEach
    void setUp() {
        admin = this.testingService.admin();
        device = this.testingService.findDefaultDevice(admin);
    }

    @Test
    void findByUser_WhenNoIntegrationExists_ReturnsEmpty() {
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(this.testingService.admin());
        
        assertThat(result).isEmpty();
    }

    @AfterEach
    void tearDown() {
        this.service.findByUser(this.testingService.admin()).ifPresent(ownTracksRecorderIntegration -> this.service.delete(ownTracksRecorderIntegration));
    }

    @Test
    void save_WhenNewIntegration_InsertsSuccessfully() {
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true,
                null,
                null,
                device.id()
        );

        OwnTracksRecorderIntegration saved = service.save(admin, integration);

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
                null,
                null,
                device.id(),
                true, now, null);

        OwnTracksRecorderIntegration saved = service.save(this.testingService.admin(), integration);

        assertThat(saved.getLastSuccessfulFetch()).isEqualTo(now);
    }

    @Test
    void findByUser_WhenIntegrationExists_ReturnsIntegration() {
        // First save an integration
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true,
                null,
                null,
                device.id()
        );
        service.save(this.testingService.admin(), integration);

        // Then find it
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(this.testingService.admin());

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
                true,
                null,
                null,
                device.id()
        );
        OwnTracksRecorderIntegration saved = service.save(this.testingService.admin(), integration);

        // Update it
        OwnTracksRecorderIntegration updated = new OwnTracksRecorderIntegration(
                saved.getId(),
                "http://localhost:8084",
                "newuser",
                "device456",
                null,
                null,
                device.id(),
                false, Instant.now(), saved.getVersion());

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
                true,
                null,
                null,
                device.id()
        );
        OwnTracksRecorderIntegration saved = service.save(this.testingService.admin(), integration);

        // Try to update with wrong version
        OwnTracksRecorderIntegration updated = new OwnTracksRecorderIntegration(
                saved.getId(),
                "http://localhost:8084",
                "newuser",
                "device456",
                null,
                null,
                device.id(),
                // Wrong version
                false, null, 999L);

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
                true,
                null,
                null,
                device.id()
        );
        OwnTracksRecorderIntegration saved = service.save(this.testingService.admin(), integration);

        // Delete it
        service.delete(saved);

        // Verify it's gone
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(this.testingService.admin());
        assertThat(result).isEmpty();
    }

    @Test
    void findByUser_WithDifferentUser_ReturnsEmpty() {
        // Save integration for first user
        OwnTracksRecorderIntegration integration = new OwnTracksRecorderIntegration(
                "http://localhost:8083",
                "testuser",
                "device123",
                true,
                null,
                null,
                device.id()
        );
        service.save(this.testingService.admin(), integration);

        // Create second user
        User otherUser = testingService.randomUser();

        // Try to find integration for second user
        Optional<OwnTracksRecorderIntegration> result = service.findByUser(otherUser);
        assertThat(result).isEmpty();
    }
}
