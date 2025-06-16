package com.rixonmathew.dataaccess.sqlite3;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for SQLite3 tests.
 * Uses a generic container with SQLite installed.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /**
     * Creates a generic container with SQLite installed.
     * The container is based on the keinos/sqlite3 image which has SQLite3 pre-installed.
     *
     * @return A generic container with SQLite installed
     */
    @Bean
    @ServiceConnection
    public GenericContainer<?> sqliteContainer() {
        return new GenericContainer<>(DockerImageName.parse("keinos/sqlite3:latest"))
                .withExposedPorts(8080) // This port isn't actually used, but needed for testcontainers
                .withCommand("--version"); // Just to keep the container running
    }
}
