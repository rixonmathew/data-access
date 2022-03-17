package com.rixon.learn.awss3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.rixon.model.instrument.Instrument;
import com.rixon.model.util.DataGeneratorUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Service
public class S3Accessor {

    private final static Logger LOGGER = LoggerFactory.getLogger(S3Accessor.class);

    private final AWSConfig awsConfig;
    @Autowired
    public S3Accessor(AWSConfig awsConfig) {
        this.awsConfig = awsConfig;
    }

    public void uploadInstrumentData(int count) {
        //Generate mock objects
        //Serialize into CSV format
        //Upload to S3 bucket
        List<Instrument> instruments = DataGeneratorUtils.randomInstruments(count);
//        List<String> headers = Arrays.asList("id","type","name","metadata");
        FileWriter fileWriter;
        try {
            Path tempFile = Files.createTempFile(Path.of("D:\\tmp"),"instruments", ".csv");
            fileWriter = new FileWriter(tempFile.toFile());
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT);
//            csvPrinter.printRecord(headers);
            instruments.forEach(instrument -> {
                try {
                    csvPrinter.printRecord(instrument.getId(),instrument.getType(),instrument.getName(),instrument.getMetadata());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            csvPrinter.close();
            LOGGER.info("Wrote the file @ [{}] ",tempFile);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.addUserMetadata("kind","instrument");
            long fileSize = tempFile.toFile().length();
            objectMetadata.setContentLength(fileSize);
            LOGGER.info("Uploading data to S3 bucket [{}] of size [{}] bytes",awsConfig.bucketName(), fileSize);
            long startTime = System.currentTimeMillis();
            AmazonS3 s3 = awsConfig.s3();
            s3.putObject(awsConfig.bucketName(),"instrument_data_"+count, new FileInputStream(tempFile.toFile()),objectMetadata);
            LOGGER.info("Done uploading in [{}] ms",System.currentTimeMillis()-startTime);
        } catch (IOException | AmazonS3Exception e) {
            e.printStackTrace();
        }


    }
}
