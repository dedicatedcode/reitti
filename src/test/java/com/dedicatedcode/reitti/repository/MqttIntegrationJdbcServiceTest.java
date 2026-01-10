package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class MqttIntegrationJdbcServiceTest {

    @Autowired
    private MqttIntegrationJdbcService mqttIntegrationJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = testingService.randomUser();
    }

    @Test
    void findByUser_whenNoIntegrationExists_returnsEmpty() {
        Optional<MqttIntegration> result = mqttIntegrationJdbcService.findByUser(testUser);
        
        assertThat(result).isEmpty();
    }

    @Test
    void save_whenCreatingNewIntegration_persistsSuccessfully() {
        MqttIntegration integration = createTestIntegration();
        
        MqttIntegration saved = mqttIntegrationJdbcService.save(testUser, integration);
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getHost()).isEqualTo("mqtt.example.com");
        assertThat(saved.getPort()).isEqualTo(1883);
        assertThat(saved.getIdentifier()).isEqualTo("test-client");
        assertThat(saved.getTopic()).isEqualTo("owntracks/+/+");
        assertThat(saved.getUsername()).isEqualTo("testuser");
        assertThat(saved.getPassword()).isEqualTo("testpass");
        assertThat(saved.getPayloadType()).isEqualTo(PayloadType.OWNTRACKS);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNull();
        assertThat(saved.getLastUsed()).isNull();
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void findByUser_afterSaving_returnsIntegration() {
        MqttIntegration integration = createTestIntegration();
        MqttIntegration saved = mqttIntegrationJdbcService.save(testUser, integration);
        
        Optional<MqttIntegration> found = mqttIntegrationJdbcService.findByUser(testUser);
        
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getHost()).isEqualTo("mqtt.example.com");
        assertThat(found.get().getPort()).isEqualTo(1883);
    }

    @Test
    void save_whenUpdatingExistingIntegration_updatesSuccessfully() {
        MqttIntegration integration = createTestIntegration();
        MqttIntegration saved = mqttIntegrationJdbcService.save(testUser, integration);
        
        MqttIntegration updated = new MqttIntegration(
            saved.getId(),
            "mqtt.updated.com",
            8883,
            "updated-client",
            "updated/topic",
            "updateduser",
            "updatedpass",
            PayloadType.OWNTRACKS,
            false,
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            saved.getLastUsed(),
            saved.getVersion()
        );
        
        MqttIntegration result = mqttIntegrationJdbcService.save(testUser, updated);
        
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getHost()).isEqualTo("mqtt.updated.com");
        assertThat(result.getPort()).isEqualTo(8883);
        assertThat(result.getIdentifier()).isEqualTo("updated-client");
        assertThat(result.getTopic()).isEqualTo("updated/topic");
        assertThat(result.getUsername()).isEqualTo("updateduser");
        assertThat(result.getPassword()).isEqualTo("updatedpass");
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getVersion()).isEqualTo(2L);
    }

    @Test
    void save_whenUpdatingWithStaleVersion_throwsOptimisticLockException() {
        MqttIntegration integration = createTestIntegration();
        MqttIntegration saved = mqttIntegrationJdbcService.save(testUser, integration);
        
        // Create an update with stale version
        MqttIntegration staleUpdate = new MqttIntegration(
            saved.getId(),
            "mqtt.stale.com",
            1883,
            "stale-client",
            "stale/topic",
            "staleuser",
            "stalepass",
            PayloadType.OWNTRACKS,
            true,
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            saved.getLastUsed(),
            999L // Stale version
        );
        
        assertThatThrownBy(() -> mqttIntegrationJdbcService.save(testUser, staleUpdate))
            .isInstanceOf(OptimisticLockException.class)
            .hasMessageContaining("MQTT integration was modified by another process");
    }

    @Test
    void findByUser_withDifferentUser_returnsEmpty() {
        MqttIntegration integration = createTestIntegration();
        mqttIntegrationJdbcService.save(testUser, integration);

        User otherUser = testingService.randomUser();
        Optional<MqttIntegration> result = mqttIntegrationJdbcService.findByUser(otherUser);
        
        assertThat(result).isEmpty();
    }

    private MqttIntegration createTestIntegration() {
        return new MqttIntegration(
            null,
            "mqtt.example.com",
            1883,
            "test-client",
            "owntracks/+/+",
            "testuser",
            "testpass",
            PayloadType.OWNTRACKS,
            true,
            null,
            null,
            null,
            null
        );
    }
}
