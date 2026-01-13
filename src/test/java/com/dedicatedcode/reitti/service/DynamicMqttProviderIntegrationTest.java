package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Testcontainers
class DynamicMqttProviderIntegrationTest {

    @Autowired
    private DynamicMqttProvider dynamicMqttProvider;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private GenericContainer mosquitto;

    private User testUser;
    private MqttIntegration mqttIntegration;
    private Mqtt3AsyncClient publisherClient;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
        String topic = "owntracks/" + testUser.getUsername() + "/testdevice";

        mqttIntegration = new MqttIntegration(
                null,
                mosquitto.getHost(),
                mosquitto.getMappedPort(1883),
                false,
                "test-client-" + System.currentTimeMillis(),
                topic,
                null,
                null,
                PayloadType.OWNTRACKS,
                true,
                Instant.now(),
                null,
                null,
                1L
        );

        // Create publisher client using the shared mosquitto container
        publisherClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("test-publisher-" + System.currentTimeMillis())
                .serverHost(mosquitto.getHost())
                .serverPort(mosquitto.getMappedPort(1883))
                .buildAsync();

        // Wait for connection to establish
        publisherClient.connect().join();
        await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> publisherClient.getState() == MqttClientState.CONNECTED);
    }

    @AfterEach
    void tearDown() {
        dynamicMqttProvider.remove(testUser);
        testingService.clearData();
    }

    @Test
    void shouldProcessValidOwnTracksLocation() {
        // Given
        String validPayload = "{\"tst\":1672574400,\"_type\":\"location\",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";

        // When
        dynamicMqttProvider.register(testUser, mqttIntegration);
        await()
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(validPayload.getBytes())
                .send()
                .join();

        // Then
        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> rawLocationPointJdbcService.findLatest(testUser).isPresent());

        List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                testUser,
                Instant.ofEpochSecond(1672574400),
                Instant.ofEpochSecond(1672574400).plusSeconds(1)
        );

        assertFalse(points.isEmpty(), "Should have at least one location point");
        RawLocationPoint point = points.getFirst();
        assertEquals(53.863149, point.getLatitude(), 0.0001);
        assertEquals(10.700927, point.getLongitude(), 0.0001);
        assertEquals(10.0, point.getAccuracyMeters(), 0.0001);
    }

    @Test
    void shouldIgnoreNonLocationOwnTracksMessage() {
        // Given
        String nonLocationPayload = "{\"tst\":1672574400,\"_type\":\"transition\"}";

        // When
        dynamicMqttProvider.register(testUser, mqttIntegration);
        await()
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(nonLocationPayload.getBytes())
                .send()
                .join();

        // Then
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                            testUser,
                            Instant.ofEpochSecond(1672574400),
                            Instant.ofEpochSecond(1672574400).plusSeconds(1)
                    );
                    return points.isEmpty();
                });
    }

    @Test
    void shouldHandleInvalidJsonGracefully() {
        // Given
        String invalidPayload = "invalid json data";

        // When
        dynamicMqttProvider.register(testUser, mqttIntegration);
        await()
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(invalidPayload.getBytes())
                .send()
                .join();

        // Then
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                            testUser,
                            Instant.ofEpochSecond(1672574400),
                            Instant.ofEpochSecond(1672574400).plusSeconds(1)
                    );
                    return points.isEmpty();
                });
    }

    @Test
    void shouldHandleLocationWithMissingRequiredFields() {
        // Given
        String incompletePayload = "{\"tst\":1672574400,\"_type\":\"location\",\"lat\":53.863149}"; // Missing lon and acc

        // When
        dynamicMqttProvider.register(testUser, mqttIntegration);
        await()
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(incompletePayload.getBytes())
                .send()
                .join();

        // Then
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                            testUser,
                            Instant.ofEpochSecond(1672574400),
                            Instant.ofEpochSecond(1672574400).plusSeconds(1)
                    );
                    return points.isEmpty();
                });
    }

    @Test
    void shouldProcessMultipleLocations() {
        // Given
        String payload1 = "{\"tst\":1672574400,\"_type\":\"location\",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";
        String payload2 = "{\"tst\":1672574460,\"_type\":\"location\",\"lat\":53.863200,\"lon\":10.700900,\"acc\":8.0}";

        // When
        dynamicMqttProvider.register(testUser, mqttIntegration);
        await()
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(payload1.getBytes())
                .send()
                .join();

        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(payload2.getBytes())
                .send()
                .join();

        // Then
        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                            testUser,
                            Instant.ofEpochSecond(1672574400),
                            Instant.ofEpochSecond(1672574460).plusSeconds(1)
                    );
                    return points.size() >= 2;
                });

        List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                testUser,
                Instant.ofEpochSecond(1672574400),
                Instant.ofEpochSecond(1672574460).plusSeconds(1)
        ).stream().filter(point -> !point.isSynthetic()).toList();

        assertEquals(2, points.size(), "Should have exactly two location points");
        assertEquals(53.863149, points.get(0).getLatitude(), 0.0001);
        assertEquals(53.863200, points.get(1).getLatitude(), 0.0001);
    }

    @Test
    void shouldIgnoreMultipleUnsupportedMessageTypes() {
        // Given - Various unsupported OwnTracks message types
        String transitionPayload = "{\"tst\":1672574400,\"_type\":\"transition\",\"event\":\"enter\"}";
        String waypointPayload = "{\"tst\":1672574400,\"_type\":\"waypoint\",\"desc\":\"Test waypoint\"}";
        String cmdPayload = "{\"tst\":1672574400,\"_type\":\"cmd\",\"action\":\"report\"}";
        String configPayload = "{\"tst\":1672574400,\"_type\":\"configuration\"}";

        // When - Register the client and send multiple unsupported messages
        dynamicMqttProvider.register(testUser, mqttIntegration);

        // Wait for the MQTT client to connect
        await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> dynamicMqttProvider.isClientConnected(testUser) == DynamicMqttProvider.MqttStatus.CONNECTED);

        // Send multiple unsupported message types
        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(transitionPayload.getBytes())
                .send()
                .join();

        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(waypointPayload.getBytes())
                .send()
                .join();

        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(cmdPayload.getBytes())
                .send()
                .join();

        publisherClient.publishWith()
                .topic(mqttIntegration.getTopic())
                .payload(configPayload.getBytes())
                .send()
                .join();

        // Then - Verify no location points were created
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                            testUser,
                            Instant.ofEpochSecond(1672574400),
                            Instant.ofEpochSecond(1672574400).plusSeconds(1)
                    );
                    return points.isEmpty();
                });

        // Additional verification
        List<RawLocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                testUser,
                Instant.ofEpochSecond(1672574400),
                Instant.ofEpochSecond(1672574400).plusSeconds(1)
        );

        assertTrue(points.isEmpty(), "Should not have any location points for unsupported message types");
    }
}