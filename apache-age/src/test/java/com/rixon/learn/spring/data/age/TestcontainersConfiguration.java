package com.rixon.learn.spring.data.age;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 18 with Apache AGE 1.8.0. The CSV fixtures are copied into /tmp/age/, the only directory AGE's
 * file loaders read from.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String AGE_IMAGE = "apache/age:release_PG18_1.8.0";

    @Bean
    PostgreSQLContainer<?> ageContainer() {
        PostgreSQLContainer<?> age = new PostgreSQLContainer<>(DockerImageName.parse(AGE_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("agedb")
                .withUsername("age")
                .withPassword("age")
                // Testcontainers replaces the image's command; keep its shared_preload_libraries=age
                .withCommand("postgres", "-c", "shared_preload_libraries=age", "-c", "fsync=off")
                .withCopyFileToContainer(MountableFile.forClasspathResource("age-import/accounts.csv"), "/tmp/age/accounts.csv")
                .withCopyFileToContainer(MountableFile.forClasspathResource("age-import/exposures.csv"), "/tmp/age/exposures.csv");
        age.start();
        return age;
    }

    @Bean
    DynamicPropertyRegistrar ageDatasourceProperties(PostgreSQLContainer<?> age) {
        return registry -> {
            registry.add("spring.datasource.url", age::getJdbcUrl);
            registry.add("spring.datasource.username", age::getUsername);
            registry.add("spring.datasource.password", age::getPassword);
        };
    }
}
