package com.rixon.learn.awss3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Testcontainers
@EnabledIfDockerAvailable
class TestS3AccessorWithLocalStack {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
            .withServices(S3);

    private ObjectStoreConfiguration objectStoreConfiguration;
    private static final String BUCKET_NAME = "instrument-market-data";

    @BeforeEach
    void setUp() {
        objectStoreConfiguration = new ObjectStoreConfiguration(
                localStack.getAccessKey(),
                localStack.getSecretKey(),
                localStack.getRegion(),
                BUCKET_NAME,
                localStack.getEndpointOverride(S3).toString()
        );

        S3Client s3 = objectStoreConfiguration.s3();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    @Test
    @DisplayName("Uploads synthesized instrument data CSV directly to S3 and verifies object metadata")
    void testUploadInstrumentData() {
        S3Accessor s3Accessor = new S3Accessor(objectStoreConfiguration);
        int recordCount = 500;
        s3Accessor.uploadInstrumentData(recordCount);

        S3Client s3 = objectStoreConfiguration.s3();
        HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key("instrument_data_" + recordCount)
                .build());

        assertThat(response).isNotNull();
        assertThat(response.contentLength()).isGreaterThan(0);
    }
}
