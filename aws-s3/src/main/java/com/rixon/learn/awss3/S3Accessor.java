package com.rixon.learn.awss3;

import com.rixon.model.instrument.Instrument;
import com.rixon.model.util.DataGeneratorUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class S3Accessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Accessor.class);

    private final ObjectStoreConfiguration objectStoreConfiguration;

    public S3Accessor(ObjectStoreConfiguration objectStoreConfiguration) {
        this.objectStoreConfiguration = objectStoreConfiguration;
    }

    public void uploadInstrumentData(int count) {
        List<Instrument> instruments = DataGeneratorUtils.randomInstruments(count);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("instruments", ".csv");
            try (FileWriter fileWriter = new FileWriter(tempFile.toFile());
                 CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT)) {
                for (Instrument instrument : instruments) {
                    csvPrinter.printRecord(instrument.getId(), instrument.getType(), instrument.getName(), instrument.getMetadata());
                }
            }

            LOGGER.info("Wrote the file @ [{}]", tempFile);
            LOGGER.info("Uploading data to S3 bucket [{}] of size [{}] bytes", objectStoreConfiguration.bucketName(), tempFile.toFile().length());
            long startTime = System.currentTimeMillis();
            S3Client s3 = objectStoreConfiguration.s3();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(objectStoreConfiguration.bucketName())
                    .key("instrument_data_" + count)
                    .build();
            s3.putObject(putObjectRequest, RequestBody.fromFile(tempFile.toFile()));
            LOGGER.info("Done uploading in [{}] ms", System.currentTimeMillis() - startTime);
        } catch (IOException e) {
            LOGGER.warn("Error processing instrument data file", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete temporary file [{}]", tempFile, e);
                }
            }
        }
    }
}
