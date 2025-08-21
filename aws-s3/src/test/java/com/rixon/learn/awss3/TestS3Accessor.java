package com.rixon.learn.awss3;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ObjectStoreConfiguration.class)

public class TestS3Accessor {
    @Autowired
    private ObjectStoreConfiguration objectStoreConfiguration;
    @Test
    @DisplayName("Test upload of CSV file to S3 bucket")
    @Disabled("To be run locally only")
    public void testS3upload() {
        S3Accessor s3Accessor = new S3Accessor(objectStoreConfiguration);
        s3Accessor.uploadInstrumentData(100000);

    }
}
