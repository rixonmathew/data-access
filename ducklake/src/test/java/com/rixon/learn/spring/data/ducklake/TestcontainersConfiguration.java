package com.rixon.learn.spring.data.ducklake;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * PostgreSQL holds the DuckLake catalog and LocalStack S3 holds its data files
 * ({@value #LAKE_BUCKET}/{@value #LAKE_PREFIX}). Inlining is disabled so every commit writes Parquet
 * to S3; tests that exercise inlining turn it back on per table.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String LAKE_BUCKET = "ducklake-it";
    public static final String LAKE_PREFIX = "lake/";
    public static final String DATA_BUCKET = "ducklake-it-data";

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
        // Buckets must exist before DuckDB attaches the lake and writes its first data file
        try (S3Client s3 = s3Client(localstack)) {
            s3.createBucket(CreateBucketRequest.builder().bucket(LAKE_BUCKET).build());
            s3.createBucket(CreateBucketRequest.builder().bucket(DATA_BUCKET).build());
        }
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
                .forcePathStyle(true)
                .build();
    }

    @Bean
    DynamicPropertyRegistrar ducklakeProperties(PostgreSQLContainer<?> postgres, LocalStackContainer localstack) {
        return registry -> {
            registry.add("ducklake.catalog.type", () -> "postgres");
            registry.add("ducklake.catalog.host", postgres::getHost);
            registry.add("ducklake.catalog.port", postgres::getFirstMappedPort);
            registry.add("ducklake.catalog.database", postgres::getDatabaseName);
            registry.add("ducklake.catalog.username", postgres::getUsername);
            registry.add("ducklake.catalog.password", postgres::getPassword);
            registry.add("ducklake.data-path", () -> "s3://" + LAKE_BUCKET + "/" + LAKE_PREFIX);
            registry.add("ducklake.data-inlining-row-limit", () -> 0);
            registry.add("ducklake.s3.endpoint", () -> localstack.getEndpointOverride(S3).getAuthority());
            registry.add("ducklake.s3.region", localstack::getRegion);
            registry.add("ducklake.s3.access-key-id", localstack::getAccessKey);
            registry.add("ducklake.s3.secret-access-key", localstack::getSecretKey);
        };
    }
}
