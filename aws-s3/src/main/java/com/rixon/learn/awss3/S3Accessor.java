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

    private final static Logger LOGGER = LoggerFactory.getLogger(S3Accessor.class);

    private final ObjectStoreConfiguration objectStoreConfiguration;
    @Autowired
    public S3Accessor(ObjectStoreConfiguration objectStoreConfiguration) {
        this.objectStoreConfiguration = objectStoreConfiguration;
    }

    public void uploadInstrumentData(int count) {
        //Generate mock objects
        //Serialize into CSV format
        //Upload to S3 bucket
        List<Instrument> instruments = DataGeneratorUtils.randomInstruments(count);
        FileWriter fileWriter;
        try {
            Path tempFile = Files.createTempFile(Path.of("/tmp"),"instruments", ".csv");
            fileWriter = new FileWriter(tempFile.toFile());
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT);
            instruments.forEach(instrument -> {
                try {
                    csvPrinter.printRecord(instrument.getId(),instrument.getType(),instrument.getName(),instrument.getMetadata());
                } catch (IOException e) {
                    LOGGER.warn("Error writing instrument data",e);
                }
            });
            csvPrinter.close();
            LOGGER.info("Wrote the file @ [{}] ",tempFile);
            LOGGER.info("Uploading data to S3 bucket [{}] of size [{}] bytes", objectStoreConfiguration.bucketName(),tempFile.toFile().length());
            long startTime = System.currentTimeMillis();
            S3Client s3 = objectStoreConfiguration.s3();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(objectStoreConfiguration.bucketName()).key("instrument_data_"+count).build();
            s3.putObject(putObjectRequest, RequestBody.fromFile(tempFile.toFile()));
            LOGGER.info("Done uploading in [{}] ms",System.currentTimeMillis()-startTime);
        } catch ( IOException e  ) {
            LOGGER.warn("Error writing file",e);
        }
    }
}
