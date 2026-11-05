package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
    void shouldGetTransportModeConfigsSortedByMinKmh() {
        List<TransportModeConfig> testConfigs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 7.0),
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 25.0),
                new TransportModeConfig(TransportMode.DRIVING, 20.0, 130.0),
                new TransportModeConfig(TransportMode.TRANSIT, 10.0, 200.0)
        );
        transportModeJdbcService.setTransportModeConfigs(testUser, testConfigs);

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);

        assertThat(configs).hasSize(4);
        assertThat(configs.get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(configs.get(0).minKmh()).isEqualTo(0.0);
        assertThat(configs.get(0).maxKmh()).isEqualTo(7.0);
        assertThat(configs.get(1).mode()).isEqualTo(TransportMode.CYCLING);
        assertThat(configs.get(2).mode()).isEqualTo(TransportMode.TRANSIT);
        assertThat(configs.get(3).mode()).isEqualTo(TransportMode.DRIVING);
    }

    @Test
    void shouldSetTransportModeConfigsAndReplaceExisting() {
        List<TransportModeConfig> newConfigs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0),
                new TransportModeConfig(TransportMode.CYCLING, 5.0, 25.0)
        );

        transportModeJdbcService.setTransportModeConfigs(testUser, newConfigs);

        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(configs).hasSize(2);
        assertThat(configs.get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(configs.get(0).maxKmh()).isEqualTo(5.0);
        assertThat(configs.get(1).mode()).isEqualTo(TransportMode.CYCLING);
        assertThat(configs.get(1).maxKmh()).isEqualTo(25.0);
    }

    @Test
    void shouldPersistPreferenceAndEnabled() {
        List<TransportModeConfig> configs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 7.0, 1.5, false, null, null)
        );

        transportModeJdbcService.setTransportModeConfigs(testUser, configs);

        List<TransportModeConfig> retrievedConfigs = transportModeJdbcService.getTransportModeConfigs(testUser);
        assertThat(retrievedConfigs).hasSize(1);
        assertThat(retrievedConfigs.get(0).preferenceWeight()).isEqualTo(1.5);
        assertThat(retrievedConfigs.get(0).enabled()).isFalse();
    }

    @Test
    void shouldCacheConfigsPerUser() {
        User user1 = testingService.randomUser();
        User user2 = testingService.randomUser();

        List<TransportModeConfig> user1Configs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 5.0)
        );
        List<TransportModeConfig> user2Configs = List.of(
                new TransportModeConfig(TransportMode.WALKING, 0.0, 10.0)
        );
        transportModeJdbcService.setTransportModeConfigs(user1, user1Configs);
        transportModeJdbcService.setTransportModeConfigs(user2, user2Configs);

        List<TransportModeConfig> user1FirstCall = transportModeJdbcService.getTransportModeConfigs(user1);
        List<TransportModeConfig> user2FirstCall = transportModeJdbcService.getTransportModeConfigs(user2);
        List<TransportModeConfig> user1SecondCall = transportModeJdbcService.getTransportModeConfigs(user1);

        assertThat(user1FirstCall).hasSize(1);
        assertThat(user2FirstCall).hasSize(1);
        assertThat(user1SecondCall).isEqualTo(user1FirstCall);
        assertThat(user1FirstCall.get(0).maxKmh()).isEqualTo(5.0);
        assertThat(user2FirstCall.get(0).maxKmh()).isEqualTo(10.0);
    }

    @Test
    void shouldStoreModeChangeSensitivity() {
        assertThat(transportModeJdbcService.getModeChangeSensitivity(testUser)).isEqualTo(2.0);

        transportModeJdbcService.setModeChangeSensitivity(testUser, 4.0);

        assertThat(transportModeJdbcService.getModeChangeSensitivity(testUser)).isEqualTo(4.0);
    }
}
