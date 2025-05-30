package com.rixonmathew.duckdb.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Utility class for creating test data files and uploading them to S3.
 */
public class TestDataUtil {
    private static final Logger logger = LoggerFactory.getLogger(TestDataUtil.class);

    /**
     * Creates a test bucket in S3.
     */
    public static String createTestBucket(S3Client s3Client) {
        String bucketName = "test-bucket-" + UUID.randomUUID();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        logger.info("Created test bucket: {}", bucketName);
        return bucketName;
    }

    /**
     * Creates a sample Parquet file with employee data and uploads it to S3.
     */
    public static String createAndUploadParquetFile(S3Client s3Client, String bucketName, int numEmployees) throws IOException, SQLException {
        // Create a temporary directory
        Path tempDir = Files.createTempDirectory("duckdb-test");
        Path parquetFile = tempDir.resolve("employees.parquet");
        
        // Create a DuckDB connection
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            // Create employees table
            createEmployeesTable(conn, numEmployees);
            
            // Export to Parquet
            try (Statement stmt = conn.createStatement()) {
                String exportQuery = String.format("COPY employees TO '%s' (FORMAT PARQUET);", parquetFile);
                stmt.execute(exportQuery);
            }
        }
        
        // Upload to S3
        String key = "employees.parquet";
        uploadFileToS3(s3Client, bucketName, key, parquetFile.toFile());
        
        // Clean up
        Files.deleteIfExists(parquetFile);
        Files.deleteIfExists(tempDir);
        
        return key;
    }

    /**
     * Creates a sample DuckDB file with employee data and uploads it to S3.
     */
    public static String createAndUploadDuckDBFile(S3Client s3Client, String bucketName, int numEmployees) throws IOException, SQLException {
        // Create a temporary directory
        Path tempDir = Files.createTempDirectory("duckdb-test");
        Path duckdbFile = tempDir.resolve("employees.duckdb");
        
        // Create a DuckDB database file
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + duckdbFile)) {
            // Create employees table
            createEmployeesTable(conn, numEmployees);
        }
        
        // Upload to S3
        String key = "employees.duckdb";
        uploadFileToS3(s3Client, bucketName, key, duckdbFile.toFile());
        
        // Clean up
        Files.deleteIfExists(duckdbFile);
        Files.deleteIfExists(tempDir);
        
        return key;
    }

    /**
     * Creates an employees table with random data.
     */
    private static void createEmployeesTable(Connection conn, int numEmployees) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Create table
            stmt.execute("CREATE TABLE employees (" +
                    "id VARCHAR PRIMARY KEY, " +
                    "first_name VARCHAR, " +
                    "last_name VARCHAR, " +
                    "email VARCHAR, " +
                    "department VARCHAR" +
                    ");");
            
            // Insert data
            StringBuilder insertQuery = new StringBuilder("INSERT INTO employees VALUES ");
            for (int i = 1; i <= numEmployees; i++) {
                String id = UUID.randomUUID().toString();
                String firstName = "FirstName" + i;
                String lastName = "LastName" + i;
                String email = "employee" + i + "@example.com";
                String department = "Department" + ((i % 10) + 1);
                
                insertQuery.append(String.format("('%s', '%s', '%s', '%s', '%s')", 
                        id, firstName, lastName, email, department));
                
                if (i < numEmployees) {
                    insertQuery.append(", ");
                }
            }
            stmt.execute(insertQuery.toString());
        }
    }

    /**
     * Uploads a file to S3.
     */
    private static void uploadFileToS3(S3Client s3Client, String bucketName, String key, File file) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
        logger.info("Uploaded file to S3: s3://{}/{}", bucketName, key);
    }
}