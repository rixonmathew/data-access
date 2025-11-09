package com.rixon.learn.awss3;

import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

public class ObjectStoreConfiguration {

    private final String accessKey;
    private final String accessSecret;
    private final String awsRegion;
    private final String s3BucketName;
    private final String endpoint;

    public ObjectStoreConfiguration(String accessKey, String accessSecret, String awsRegion, String s3BucketName, String endpoint) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
        this.awsRegion = awsRegion;
        this.s3BucketName = s3BucketName;
        this.endpoint = endpoint;
    }

    public S3Client s3() {
        Assert.notNull(accessKey,"AWS Access Key Cannot be null. Check configuration");
        Assert.notNull(accessSecret,"AWS Access Secret Cannot be null. Check configuration");
        Assert.notNull(awsRegion,"AWS Region cannot be null. Check configuration");
        Assert.notNull(s3BucketName,"AWS s3BucketName cannot be null. Check configuration");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, accessSecret);
        if (endpoint!=null && endpoint.startsWith("http://")) {

            return S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of(awsRegion))
                    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .build();
        } else
            return S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of(awsRegion))
                    .build();
    }

    public String bucketName() {
        return s3BucketName;
    }


}
