package com.rixon.learn.awss3;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
public class AWSConfig {

    @Value("${aws.accessKey}")
    private String accessKey;
    @Value("${aws.accessSecret}")
    private String accessSecret;
    @Value("${aws.region}")
    private String awsRegion;
    @Value("${aws.bucketName}")
    private String s3BucketName;

    public AmazonS3 s3() {
        Assert.notNull(accessKey,"AWS Access Key Cannot be null. Check configuration");
        Assert.notNull(accessSecret,"AWS Access Secret Cannot be null. Check configuration");
        Assert.notNull(awsRegion,"AWS Region cannot be null. Check configuration");
        Assert.notNull(s3BucketName,"AWS s3BucketName cannot be null. Check configuration");
        AWSCredentials credentials = new BasicAWSCredentials(accessKey,accessSecret);
        return AmazonS3ClientBuilder.standard()
                .withRegion(awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }

    public String bucketName() {
        return s3BucketName;
    }


}
