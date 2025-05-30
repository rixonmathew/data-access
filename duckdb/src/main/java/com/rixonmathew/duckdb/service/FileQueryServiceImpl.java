package com.rixonmathew.duckdb.service;

import com.rixonmathew.duckdb.model.PerformanceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
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

    @Override
    public List<Map<String, Object>> queryParquetFile(String s3Bucket, String s3Key, String query) {
        logger.info("Querying Parquet file: s3://{}/{} with query: {}", s3Bucket, s3Key, query);

        try {
            // Use DuckDB to query the Parquet file directly from S3
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
                try (Statement stmt = conn.createStatement()) {
                    // Install and load httpfs extension
                    stmt.execute("INSTALL httpfs;");
                    stmt.execute("LOAD httpfs;");

                    // Set S3 credentials
                    stmt.execute(String.format("SET s3_region='%s';", region));
                    stmt.execute(String.format("SET s3_access_key_id='%s';", accessKeyId));
                    stmt.execute(String.format("SET s3_secret_access_key='%s';", secretKey));

                    // If using a custom endpoint (e.g., LocalStack)
                    if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
                        stmt.execute(String.format("SET s3_endpoint='%s';", s3Endpoint));
                    }

                    // Register the Parquet file as a table directly from S3
                    String tableName = "employees";
                    String s3Url = String.format("s3://%s/%s", s3Bucket, s3Key);
                    String registerQuery = String.format("CREATE OR REPLACE TABLE %s AS SELECT * FROM read_parquet('%s');", 
                                                        tableName, s3Url);
                    stmt.execute(registerQuery);

                    // Execute the actual query
                    return executeQuery(stmt, query);
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying Parquet file", e);
            throw new RuntimeException("Error querying Parquet file", e);
        }
    }

    @Override
    public List<Map<String, Object>> queryDuckDBFile(String s3Bucket, String s3Key, String query) {
        logger.info("Querying DuckDB file: s3://{}/{} with query: {}", s3Bucket, s3Key, query);

        try {
            // Download the DuckDB file from S3 to a temporary location
            Path tempFile = downloadFromS3(s3Bucket, s3Key);

            // Connect to the DuckDB file
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + tempFile.toAbsolutePath())) {
                try (Statement stmt = conn.createStatement()) {
                    // Execute the query
                    return executeQuery(stmt, query);
                }
            } finally {
                // Clean up the temporary file
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException | SQLException e) {
            logger.error("Error querying DuckDB file", e);
            throw new RuntimeException("Error querying DuckDB file", e);
        }
    }

    @Override
    public PerformanceResult compareQueryPerformance(String s3Bucket, String parquetKey, String duckdbKey, String query) {
        logger.info("Comparing query performance between Parquet and DuckDB files");
        logger.info("Parquet file: s3://{}/{}", s3Bucket, parquetKey);
        logger.info("DuckDB file: s3://{}/{}", s3Bucket, duckdbKey);
        logger.info("Query: {}", query);

        // Query Parquet file and measure time
        Instant parquetStart = Instant.now();
        List<Map<String, Object>> parquetResults = queryParquetFile(s3Bucket, parquetKey, query);
        Duration parquetQueryTime = Duration.between(parquetStart, Instant.now());
        logger.info("Parquet query completed in {}ms", parquetQueryTime.toMillis());

        // Query DuckDB file and measure time
        Instant duckdbStart = Instant.now();
        List<Map<String, Object>> duckdbResults = queryDuckDBFile(s3Bucket, duckdbKey, query);
        Duration duckdbQueryTime = Duration.between(duckdbStart, Instant.now());
        logger.info("DuckDB query completed in {}ms", duckdbQueryTime.toMillis());

        // Create and return the performance result
        PerformanceResult result = new PerformanceResult(
                parquetQueryTime,
                duckdbQueryTime,
                parquetResults,
                duckdbResults,
                query
        );

        logger.info("Performance comparison results: {}", result);
        return result;
    }

    /**
     * Downloads a file from S3 to a temporary location.
     */
    private Path downloadFromS3(String bucket, String key) throws IOException {
        logger.debug("Downloading file from S3: s3://{}/{}", bucket, key);

        // Create a temporary file
        Path tempFile = Files.createTempFile("s3-download-", getFileExtension(key));

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

    /**
     * Extracts the file extension from a key.
     */
    private String getFileExtension(String key) {
        int lastDotIndex = key.lastIndexOf('.');
        return lastDotIndex > 0 ? key.substring(lastDotIndex) : "";
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
