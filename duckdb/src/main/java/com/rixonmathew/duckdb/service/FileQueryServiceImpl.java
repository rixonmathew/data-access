package com.rixonmathew.duckdb.service;

import com.rixonmathew.duckdb.model.PerformanceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileQueryServiceImpl implements FileQueryService {

    private static final Logger logger = LoggerFactory.getLogger(FileQueryServiceImpl.class);
    private final S3Client s3Client;

    @Value("${aws.s3.endpoint:#{null}}")
    private String s3Endpoint;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.accessKeyId:test}")
    private String accessKeyId;

    @Value("${aws.secretKey:test}")
    private String secretKey;

    public FileQueryServiceImpl(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Downloads a file from S3 to a temporary location.
     * This is used for testing with LocalStack since direct S3 access doesn't work correctly with LocalStack.
     */
    private Path downloadFromS3(String bucket, String key) throws IOException {
        logger.debug("Downloading file from S3: s3://{}/{}", bucket, key);

        // Create a temporary file with the appropriate extension
        String extension = key.substring(key.lastIndexOf('.'));
        Path tempFile = Files.createTempFile("s3-download-", extension);

        // Download the file from S3
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
             FileOutputStream outputStream = new FileOutputStream(tempFile.toFile())) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = s3Object.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        logger.debug("File downloaded to: {}", tempFile);
        return tempFile;
    }

    @Override
    public List<Map<String, Object>> queryParquetFile(String s3Bucket, String s3Key, String query) {
        logger.info("Querying Parquet file: s3://{}/{} with query: {}", s3Bucket, s3Key, query);

        try {
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
                try (Statement stmt = conn.createStatement()) {
                    // Install and load httpfs extension
                    stmt.execute("INSTALL httpfs;");
                    stmt.execute("LOAD httpfs;");

                    String createSecretCommandForDuckS3 = getSecretCommandForDuckS3();
                    stmt.execute(createSecretCommandForDuckS3);

                    // Standard S3 URL format
                    String s3Url = String.format("s3://%s/%s", s3Bucket, s3Key);

                    // Register the Parquet file as a table directly from S3
                    String tableName = "employees";
                    String registerQuery = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_parquet('%s');",
                            tableName, s3Url);
                    stmt.execute(registerQuery);

                    // Execute the actual query
                    return executeQuery(stmt, query);
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying Parquet file directly from S3", e);
            throw new RuntimeException("Error querying Parquet file directly from S3", e);
        }
    }

    private String getSecretCommandForDuckS3() {
        String createSecretCommandForDuckS3 = String.format("""
                CREATE OR REPLACE SECRET secret (
                    TYPE s3,
                    PROVIDER config,
                    KEY_ID '%s',
                    SECRET '%s',
                    ENDPOINT '%s',
                    URL_STYLE 'path',
                    USE_SSL 'false',
                    REGION '%s'
                );;""", accessKeyId, secretKey, s3Endpoint.replace("http://", ""), region);
        return createSecretCommandForDuckS3;
    }

    @Override
    public List<Map<String, Object>> queryDuckDBFile(String s3Bucket, String s3Key, String query) {
        logger.info("Querying DuckDB file: s3://{}/{} with query: {}", s3Bucket, s3Key, query);

        try {
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
                try (Statement stmt = conn.createStatement()) {
                    // Install and load httpfs extension
                    stmt.execute("INSTALL httpfs;");
                    stmt.execute("LOAD httpfs;");

                    String createSecretCommandForDuckS3 = getSecretCommandForDuckS3();
                    stmt.execute(createSecretCommandForDuckS3);

                    // Standard S3 URL format
                    String s3Url = String.format("s3://%s/%s", s3Bucket, s3Key);

                    // Connect to the DuckDB file directly from S3
                    stmt.execute(String.format("ATTACH DATABASE '%s' AS s3db;", s3Url));
                    stmt.execute("use s3db");

                    // Execute the query
                    return executeQuery(stmt, query);
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying DuckDB file directly from S3", e);
            throw new RuntimeException("Error querying DuckDB file directly from S3", e);
        }
    }

    @Override
    public PerformanceResult compareQueryPerformance(String s3Bucket, String parquetKey, String duckdbKey, String query, int numRuns) {
        logger.info("Comparing query performance between Parquet and DuckDB files");
        logger.info("Parquet file: s3://{}/{}", s3Bucket, parquetKey);
        logger.info("DuckDB file: s3://{}/{}", s3Bucket, duckdbKey);
        logger.info("Query: {}", query);
        logger.info("Number of runs: {}", numRuns);

        List<Duration> parquetQueryTimes = new ArrayList<>();
        List<Duration> duckdbQueryTimes = new ArrayList<>();
        List<Map<String, Object>> parquetResults = null;
        List<Map<String, Object>> duckdbResults = null;

        // Run queries multiple times
        for (int i = 0; i < numRuns; i++) {
            logger.info("Run {} of {}", i + 1, numRuns);

            // Query Parquet file and measure time
            Instant parquetStart = Instant.now();
            List<Map<String, Object>> currentParquetResults = queryParquetFile(s3Bucket, parquetKey, query);
            Duration parquetQueryTime = Duration.between(parquetStart, Instant.now());
            parquetQueryTimes.add(parquetQueryTime);
            logger.info("Parquet query completed in {}ms", parquetQueryTime.toMillis());

            // Save the results from the first run
            if (i == 0) {
                parquetResults = currentParquetResults;
            }

            // Query DuckDB file and measure time
            Instant duckdbStart = Instant.now();
            List<Map<String, Object>> currentDuckdbResults = queryDuckDBFile(s3Bucket, duckdbKey, query);
            Duration duckdbQueryTime = Duration.between(duckdbStart, Instant.now());
            duckdbQueryTimes.add(duckdbQueryTime);
            logger.info("DuckDB query completed in {}ms", duckdbQueryTime.toMillis());

            // Save the results from the first run
            if (i == 0) {
                duckdbResults = currentDuckdbResults;
            }
        }

        // Create and return the performance result
        PerformanceResult result = new PerformanceResult(
                parquetQueryTimes,
                duckdbQueryTimes,
                parquetResults,
                duckdbResults,
                query
        );

        logger.info("Performance comparison results: {}", result);
        return result;
    }


    /**
     * Executes a SQL query and converts the result set to a list of maps.
     */
    private List<Map<String, Object>> executeQuery(Statement stmt, String query) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();

        try (ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Process each row
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();

                // Process each column
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }

                results.add(row);
            }
        }

        return results;
    }
}
