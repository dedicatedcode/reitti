package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TransportModeJdbcServiceTest {

    @Autowired
    private TransportModeJdbcService transportModeJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void shouldGetTransportModeConfigsSortedByMaxKmhNullsLast() {
        // Given - set up test configs for the random user
        List<TransportModeConfig> testConfigs = List.of(
            new TransportModeConfig(TransportMode.WALKING, 7.0),
            new TransportModeConfig(TransportMode.CYCLING, 20.0),
            new TransportModeConfig(TransportMode.DRIVING, 120.0),
            new TransportModeConfig(TransportMode.TRANSIT, null)
        );
        transportModeJdbcService.setTransportModeConfigs(testUser, testConfigs);

        // When
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);

        // Then
        assertThat(configs).hasSize(4);
        assertThat(configs.get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(configs.get(0).maxKmh()).isEqualTo(7.0);
        assertThat(configs.get(1).mode()).isEqualTo(TransportMode.CYCLING);
        assertThat(configs.get(1).maxKmh()).isEqualTo(20.0);
        assertThat(configs.get(2).mode()).isEqualTo(TransportMode.DRIVING);
        assertThat(configs.get(2).maxKmh()).isEqualTo(120.0);
        assertThat(configs.get(3).mode()).isEqualTo(TransportMode.TRANSIT);
        assertThat(configs.get(3).maxKmh()).isNull();
    }

    @Test
    void shouldSetTransportModeConfigsAndReplaceExisting() {
        // Given
        List<TransportModeConfig> newConfigs = List.of(
            new TransportModeConfig(TransportMode.WALKING, 5.0),
            new TransportModeConfig(TransportMode.CYCLING, 25.0)
        );

        // When
        transportModeJdbcService.setTransportModeConfigs(testUser, newConfigs);

        // Then
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(configs).hasSize(2);
        assertThat(configs.get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(configs.get(0).maxKmh()).isEqualTo(5.0);
        assertThat(configs.get(1).mode()).isEqualTo(TransportMode.CYCLING);
        assertThat(configs.get(1).maxKmh()).isEqualTo(25.0);
    }

    @Test
    void shouldHandleNullMaxKmhValues() {
        // Given
        List<TransportModeConfig> configs = List.of(
            new TransportModeConfig(TransportMode.WALKING, 7.0),
            new TransportModeConfig(TransportMode.TRANSIT, null)
        );

        // When
        transportModeJdbcService.setTransportModeConfigs(testUser, configs);

        // Then
        List<TransportModeConfig> retrievedConfigs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(retrievedConfigs).hasSize(2);
        assertThat(retrievedConfigs.get(0).maxKmh()).isEqualTo(7.0);
        assertThat(retrievedConfigs.get(1).maxKmh()).isNull();
    }

    @Test
    void shouldReturnEmptyListForUserWithNoConfigs() {
        // Given
        User randomUser = testingService.randomUser();

        // When
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(randomUser);

        // Then
        assertThat(configs).isEmpty();
    }

    @Test
    void shouldCacheConfigsPerUser() {
        // Given
        User user1 = testingService.randomUser();
        User user2 = testingService.randomUser();
        
        List<TransportModeConfig> user1Configs = List.of(
            new TransportModeConfig(TransportMode.WALKING, 5.0)
        );
        List<TransportModeConfig> user2Configs = List.of(
            new TransportModeConfig(TransportMode.WALKING, 10.0)
        );
        transportModeJdbcService.setTransportModeConfigs(user1, user1Configs);
        transportModeJdbcService.setTransportModeConfigs(user2, user2Configs);

        // When - get configs for both users
        List<TransportModeConfig> user1FirstCall = transportModeJdbcService.getTransportModeConfigs(user1);
        List<TransportModeConfig> user2FirstCall = transportModeJdbcService.getTransportModeConfigs(user2);
        List<TransportModeConfig> user1SecondCall = transportModeJdbcService.getTransportModeConfigs(user1);

        // Then - each user should have their own cached configs
        assertThat(user1FirstCall).hasSize(1);
        assertThat(user2FirstCall).hasSize(1);
        assertThat(user1SecondCall).isEqualTo(user1FirstCall); // should be same cached result
        assertThat(user1FirstCall.get(0).maxKmh()).isEqualTo(5.0);
        assertThat(user2FirstCall.get(0).maxKmh()).isEqualTo(10.0);
    }
}
