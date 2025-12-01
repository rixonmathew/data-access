package com.rixon.learn.awss3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.net.HttpURLConnection;
import java.net.URL;

public class TestS3AccessorWithMinio {

    private ObjectStoreConfiguration minioAWSConfig;
    private String endpoint;

    @BeforeEach
    public void setup(){

        String aws_access_key_id="5YO2B3OFTBDBSO4F4AVT";
        String aws_secret_access_key="jghuCEoT7rBkB3F5yfPWNKzcTLllOa+2J+obKgNb";
        String aws_region="us-east-1";
        String s3_bucket_name="testbucket";
        endpoint = "http://rixp330-ubuntu:9000";
        minioAWSConfig = new ObjectStoreConfiguration(aws_access_key_id, aws_secret_access_key, aws_region, s3_bucket_name, endpoint);

    }

    @Test
    @DisplayName("Test upload of CSV file to S3 bucket")
    public void testS3upload() {
        // Skip test if endpoint is not reachable
        Assumptions.assumeTrue(isEndpointReachable(endpoint), "MinIO endpoint is not reachable; skipping test");

        S3Accessor s3Accessor = new S3Accessor(minioAWSConfig);
        s3Accessor.uploadInstrumentData(100000);

    }

    private boolean isEndpointReachable(String endpointUrl) {
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            int code = connection.getResponseCode();
            return code > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
