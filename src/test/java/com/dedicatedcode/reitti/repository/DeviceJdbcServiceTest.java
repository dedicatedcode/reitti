package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ActiveProfiles("test")
@Transactional
class DeviceJdbcServiceTest {

    @Autowired
    private DeviceJdbcService deviceJdbcService;
    @Autowired
    private TestingService testingService;

    @Test
    void shouldInsertAndFindDevice() {
        // Given
        User user = testingService.randomUser();
        Device device = new Device(
                null,
                "Test Device",
                true,
                true,
                "#FF5733",
                Instant.now(),
                Instant.now(),
                null
        );

        // When
        Device saved = deviceJdbcService.save(device, user);
        List<Device> found = deviceJdbcService.getAll(user);

        // Then
        assertThat(found).hasSize(1);
        Device foundDevice = found.getFirst();
        assertThat(foundDevice.id()).isEqualTo(saved.id());
        assertThat(foundDevice.name()).isEqualTo("Test Device");
        assertThat(foundDevice.color()).isEqualTo("#FF5733");
        assertThat(foundDevice.enabled()).isTrue();
        assertThat(foundDevice.showOnMap()).isTrue();
    }

    @Test
    void shouldReturnEmptyListWhenNoDeviceFound() {
        // Given
        User user = testingService.randomUser();

        // When
        List<Device> found = deviceJdbcService.getAll(user);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateDevice() {
        // Given
        User user = testingService.randomUser();
        Device device = new Device(
                null,
                "Original Name",
                true,
                true,
                "#FF5733",
                Instant.now(),
                Instant.now(),
                null
        );
        Device saved = deviceJdbcService.save(device, user);

        Device updatedDevice = new Device(
                saved.id(),
                "Updated Name",
                false,
                false,
                "#00FF00",
                saved.createdAt(),
                saved.updatedAt(),
                saved.version()
        );

        // When
        Device result = deviceJdbcService.update(updatedDevice, user);
        List<Device> found = deviceJdbcService.getAll(user);

        // Then
        assertThat(found).hasSize(1);
        Device foundDevice = found.getFirst();
        assertThat(foundDevice.name()).isEqualTo("Updated Name");
        assertThat(foundDevice.color()).isEqualTo("#00FF00");
        assertThat(foundDevice.enabled()).isFalse();
        assertThat(foundDevice.showOnMap()).isFalse();
        assertThat(foundDevice.version()).isEqualTo(saved.version() + 1);
    }

    @Test
    void shouldDeleteDevice() {
        // Given
        User user = testingService.randomUser();
        Device device = new Device(
                null,
                "To Delete",
                true,
                true,
                "#FF5733",
                Instant.now(),
                Instant.now(),
                null
        );
        Device saved = deviceJdbcService.save(device, user);

        // When
        deviceJdbcService.delete(saved, user);
        List<Device> found = deviceJdbcService.getAll(user);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldGetAllEnabledDevices() {
        // Given
        User user = testingService.randomUser();
        Device enabledDevice = new Device(
                null,
                "Enabled Device",
                true,
                true,
                "#FF5733",
                Instant.now(),
                Instant.now(),
                null
        );
        Device disabledDevice = new Device(
                null,
                "Disabled Device",
                false,
                true,
                "#00FF00",
                Instant.now(),
                Instant.now(),
                null
        );
        deviceJdbcService.save(enabledDevice, user);
        deviceJdbcService.save(disabledDevice, user);

        // When
        List<Device> allEnabled = deviceJdbcService.getAllEnabled(user);

        // Then
        assertThat(allEnabled).hasSize(1);
        assertThat(allEnabled.getFirst().name()).isEqualTo("Enabled Device");
    }

    @Test
    void shouldReturnEmptyListWhenNoEnabledDeviceFound() {
        // Given
        User user = testingService.randomUser();

        // When
        List<Device> found = deviceJdbcService.getAllEnabled(user);

        // Then
        assertThat(found).isEmpty();
    }
}
