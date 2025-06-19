package com.dedicatedcode.reitti;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfiguration {
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> timescaledb() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:17-3.5-alpine")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("reitti")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitmq() {
        return new RabbitMQContainer("rabbitmq:3-management")
                .withExposedPorts(5672, 15672);

    }

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return new RedisContainer("redis:7-alpine");
    }
}
