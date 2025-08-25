package com.rixon.learn.awss3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TestS3Accessor {
    private ObjectStoreConfiguration objectStoreConfiguration;

    @BeforeEach
    public void setup(){
        objectStoreConfiguration = new ObjectStoreConfiguration("accessKey", "accessSecret", "us-east-1", "s3BucketName", "http://localhost:4566");
    }
    @Test
    @DisplayName("Test upload of CSV file to S3 bucket")
    @Disabled("To be run locally only")
    public void testS3upload() {
        S3Accessor s3Accessor = new S3Accessor(objectStoreConfiguration);
        s3Accessor.uploadInstrumentData(100000);

    }
}
