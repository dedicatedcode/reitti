package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.config.TestContainersConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> timescaledb = new PostgreSQLContainer<>("timescale/timescaledb:latest-pg14")
            .withDatabaseName("reitti_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres -c shared_preload_libraries=timescaledb");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Database properties
        registry.add("spring.datasource.url", timescaledb::getJdbcUrl);
        registry.add("spring.datasource.username", timescaledb::getUsername);
        registry.add("spring.datasource.password", timescaledb::getPassword);
        
        // RabbitMQ properties
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }
}
