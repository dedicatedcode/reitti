package com.dedicatedcode.reitti.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> timescaledbContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("timescale/timescaledb:latest-pg14"))
                .withDatabaseName("reitti_test")
                .withUsername("test")
                .withPassword("test")
                .withCommand("postgres -c shared_preload_libraries=timescaledb");
    }

    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"))
                .withExposedPorts(5672, 15672);
    }
}
