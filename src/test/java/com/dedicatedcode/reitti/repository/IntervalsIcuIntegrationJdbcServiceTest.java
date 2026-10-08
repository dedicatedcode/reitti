package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class IntervalsIcuIntegrationJdbcServiceTest {

    @Autowired
    private IntervalsIcuIntegrationJdbcService service;

    @Autowired
    private IntervalsIcuImportedActivityJdbcService importedActivityService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        this.user = this.testingService.randomUser();
        this.device = this.testingService.findDefaultDevice(user);
    }

    @Test
    void findByUser_WhenNoIntegrationExists_ReturnsEmpty() {
        assertThat(service.findByUser(user)).isEmpty();
    }

    @Test
    void save_WhenNewIntegration_InsertsSuccessfully() {
        IntervalsIcuIntegration integration = new IntervalsIcuIntegration(
                "secret-key", device.id(), true);

        IntervalsIcuIntegration saved = service.save(user, integration);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getApiKey()).isEqualTo("secret-key");
        assertThat(saved.getReittiDeviceId()).isEqualTo(device.id());
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getLastSuccessfulFetch()).isNull();
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void findByUser_WhenIntegrationExists_RoundTripsAllFields() {
        Instant lastFetch = Instant.parse("2024-11-20T08:15:30Z");
        service.save(user, new IntervalsIcuIntegration(null, "secret-key", device.id(),
                "i12345", "Test Athlete", true, lastFetch, 1L));

        Optional<IntervalsIcuIntegration> result = service.findByUser(user);

        assertThat(result).isPresent();
        IntervalsIcuIntegration found = result.get();
        assertThat(found.getApiKey()).isEqualTo("secret-key");
        assertThat(found.getAthleteId()).isEqualTo("i12345");
        assertThat(found.getAthleteName()).isEqualTo("Test Athlete");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getLastSuccessfulFetch()).isEqualTo(lastFetch);
    }

    @Test
    void findByUser_WithDifferentUser_ReturnsEmpty() {
        service.save(user, new IntervalsIcuIntegration("secret-key", device.id(), true));

        assertThat(service.findByUser(testingService.randomUser())).isEmpty();
    }

    @Test
    void update_WhenIntegrationExists_BumpsVersion() {
        IntervalsIcuIntegration saved = service.save(user,
                new IntervalsIcuIntegration("secret-key", device.id(), true));

        IntervalsIcuIntegration result = service.update(user, new IntervalsIcuIntegration(saved.getId(),
                "rotated-key", device.id(), "i12345", "Test Athlete", false,
                Instant.parse("2024-11-20T08:15:30Z"), saved.getVersion()));

        assertThat(result.getVersion()).isEqualTo(2L);
        assertThat(result.getApiKey()).isEqualTo("rotated-key");
        assertThat(result.getAthleteId()).isEqualTo("i12345");
        assertThat(result.isEnabled()).isFalse();

        IntervalsIcuIntegration reloaded = service.findByUser(user).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(2L);
        assertThat(reloaded.getApiKey()).isEqualTo("rotated-key");
    }

    @Test
    void update_WithWrongVersion_ThrowsOptimisticLockException() {
        IntervalsIcuIntegration saved = service.save(user,
                new IntervalsIcuIntegration("secret-key", device.id(), true));

        IntervalsIcuIntegration stale = new IntervalsIcuIntegration(saved.getId(), "other",
                device.id(), null, null, true, null, 999L);

        assertThatThrownBy(() -> service.update(user, stale))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Optimistic locking failure");
    }

    @Test
    void markImported_IsIdempotentPerActivity() {
        importedActivityService.markImported(user, "i1", LocalDateTime.parse("2024-11-19T07:35:18"), 42L);
        importedActivityService.markImported(user, "i1", LocalDateTime.parse("2024-11-19T07:35:18"), 42L);
        importedActivityService.markImported(user, "i2", null, 0L);

        assertThat(importedActivityService.findImportedIds(user, List.of("i1", "i2", "i3")))
                .containsExactlyInAnyOrder("i1", "i2");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM intervals_icu_imported_activities WHERE user_id = ?", Integer.class, user.getId());
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findImportedIds_WithUnknownIdsOrEmptyInput_ReturnsEmpty() {
        assertThat(importedActivityService.findImportedIds(user, List.of())).isEmpty();
        assertThat(importedActivityService.findImportedIds(user, List.of("nope"))).isEqualTo(Set.of());
    }

    @Test
    void importedActivities_AreScopedPerUser() {
        User other = testingService.randomUser();
        importedActivityService.markImported(user, "i1", LocalDateTime.parse("2024-11-19T07:35:18"), 1L);

        assertThat(importedActivityService.findImportedIds(other, List.of("i1"))).isEmpty();
    }
}
