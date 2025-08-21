package com.rixon.learn.awss3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TestS3AccessorWithMinio {

    private ObjectStoreConfiguration minioAWSConfig;

    @BeforeEach
    public void setup(){

        String aws_access_key_id="5YO2B3OFTBDBSO4F4AVT";
        String aws_secret_access_key="jghuCEoT7rBkB3F5yfPWNKzcTLllOa+2J+obKgNb";
        String aws_region="us-east-1";
        String s3_bucket_name="testbucket";
        String endpoint = "http://rixp330-ubuntu:9000";
        minioAWSConfig = new ObjectStoreConfiguration(aws_access_key_id, aws_secret_access_key, aws_region, s3_bucket_name, endpoint);

    }

    @Test
    @DisplayName("Test upload of CSV file to S3 bucket")
    public void testS3upload() {
        S3Accessor s3Accessor = new S3Accessor(minioAWSConfig);
        s3Accessor.uploadInstrumentData(100000);

    }
}
