package com.gozzerks.payflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for integration tests.
 * <p>
 * Provides PostgreSQL and RabbitMQ containers that are:
 * <ul>
 *   <li>Automatically started before tests</li>
 *   <li>Auto-configured via @ServiceConnection</li>
 *   <li>Reused across test runs for performance</li>
 *   <li>Automatically cleaned up after test execution</li>
 * </ul>
 * <p>
 * Containers are managed by Testcontainers' Ryuk (Resource Reaper) for reliable cleanup.
 * No manual try-catch or cleanup required - follows fail-fast principle for test reliability.
 *
 * @see ServiceConnection
 * @see TestConfiguration
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TestcontainersConfiguration.class);

    /**
     * Creates a PostgreSQL container for database integration tests.
     * <p>
     * Uses @ServiceConnection to automatically configure:
     * <ul>
     *   <li>spring.datasource.url</li>
     *   <li>spring.datasource.username</li>
     *   <li>spring.datasource.password</li>
     * </ul>
     * <p>
     * Note: Suppressing "resource" warning because Spring manages the container lifecycle.
     * Container is closed when ApplicationContext shuts down, not in try-with-resources.
     *
     * @return configured PostgreSQL container
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // Spring manages container lifecycle - closed on context shutdown
    PostgreSQLContainer<?> postgresContainer() {
        log.info("Initializing PostgreSQL testcontainer (postgres:16-alpine)");
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Reuse container across test runs for faster execution
    }

    /**
     * Creates a RabbitMQ container for messaging integration tests.
     * <p>
     * Uses @ServiceConnection to automatically configure:
     * <ul>
     *   <li>spring.rabbitmq.host</li>
     *   <li>spring.rabbitmq.port</li>
     *   <li>spring.rabbitmq.username</li>
     *   <li>spring.rabbitmq.password</li>
     * </ul>
     * <p>
     * Note: Suppressing "resource" warning because Spring manages the container lifecycle.
     * Container is closed when ApplicationContext shuts down, not in try-with-resources.
     *
     * @return configured RabbitMQ container with management plugin
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // Spring manages container lifecycle - closed on context shutdown
    RabbitMQContainer rabbitContainer() {
        log.info("Initializing RabbitMQ testcontainer (rabbitmq:3.13-management-alpine)");
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withReuse(true); // Reuse container across test runs for faster execution
    }
}