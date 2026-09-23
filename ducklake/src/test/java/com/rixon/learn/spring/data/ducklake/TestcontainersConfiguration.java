package com.rixon.learn.spring.data.ducklake;

import com.rixon.learn.spring.data.ducklake.config.DuckDBConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
                .withDatabaseName("ducklake_catalog")
                .withUsername("ducklake")
                .withPassword("ducklake");
        postgres.start();
        return postgres;
    }

    @Bean
    LocalStackContainer localStackContainer() {
        LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
                .withServices(S3);
        localstack.start();
        return localstack;
    }

    @Bean
    S3Client s3Client(LocalStackContainer localStackContainer) {
        return S3Client.builder()
                .endpointOverride(localStackContainer.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                ))
                .region(Region.of(localStackContainer.getRegion()))
                .build();
    }

    @Bean
    DuckDBConfig duckDBConfig(PostgreSQLContainer<?> postgresContainer) {
        DuckDBConfig config = new DuckDBConfig();
        config.setCatalogType("postgres");
        config.setCatalogJdbcUrl(postgresContainer.getJdbcUrl());
        config.setCatalogUsername(postgresContainer.getUsername());
        config.setCatalogPassword(postgresContainer.getPassword());
        config.setHost(postgresContainer.getHost());
        config.setPort(postgresContainer.getFirstMappedPort());
        config.setDatabase(postgresContainer.getDatabaseName());
        config.setCatalogDataFilesPath("data_files/");
        return config;
    }
}
