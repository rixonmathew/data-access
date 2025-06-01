package com.rixonmathew.duckdb.service;

import com.rixonmathew.duckdb.model.PerformanceResult;
import com.rixonmathew.duckdb.util.TestDataUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@SpringBootTest
@Testcontainers
public class FileQueryServiceIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FileQueryServiceIntegrationTest.class);
    private static final int NUM_EMPLOYEES = 100000;
    private static String bucketName;
    private static String parquetKey;
    private static String duckdbKey;

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4.0"))
            .withServices(S3);

    @Autowired
    private FileQueryService fileQueryService;

    @Autowired
    private S3Client s3Client;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.s3.endpoint", () -> localstack.getEndpointOverride(S3).toString());
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.accessKeyId", localstack::getAccessKey);
        registry.add("aws.secretKey", localstack::getSecretKey);
    }

    @BeforeAll
    static void setup(@Autowired S3Client s3Client) throws IOException, SQLException {
        logger.info("Setting up test data");

        // Create test bucket
        bucketName = TestDataUtil.createTestBucket(s3Client);

        // Create and upload test files
        parquetKey = TestDataUtil.createAndUploadParquetFile(s3Client, bucketName, NUM_EMPLOYEES);
        duckdbKey = TestDataUtil.createAndUploadDuckDBFile(s3Client, bucketName, NUM_EMPLOYEES);

        logger.info("Test data setup complete");
        logger.info("Bucket: {}", bucketName);
        logger.info("Parquet file: {}", parquetKey);
        logger.info("DuckDB file: {}", duckdbKey);
    }

    @Test
    void testQueryParquetFile() {
        String query = "SELECT * FROM employees LIMIT 10";
        List<Map<String, Object>> results = fileQueryService.queryParquetFile(bucketName, parquetKey, query);

        assertNotNull(results);
        assertEquals(10, results.size());

        // Verify the structure of the results
        Map<String, Object> firstRow = results.get(0);
        assertTrue(firstRow.containsKey("id"));
        assertTrue(firstRow.containsKey("first_name"));
        assertTrue(firstRow.containsKey("last_name"));
        assertTrue(firstRow.containsKey("email"));
        assertTrue(firstRow.containsKey("department"));
    }

    @Test
    void testQueryDuckDBFile() {
        String query = "SELECT * FROM employees LIMIT 10";
        List<Map<String, Object>> results = fileQueryService.queryDuckDBFile(bucketName, duckdbKey, query);

        assertNotNull(results);
        assertEquals(10, results.size());

        // Verify the structure of the results
        Map<String, Object> firstRow = results.get(0);
        assertTrue(firstRow.containsKey("id"));
        assertTrue(firstRow.containsKey("first_name"));
        assertTrue(firstRow.containsKey("last_name"));
        assertTrue(firstRow.containsKey("email"));
        assertTrue(firstRow.containsKey("department"));
    }

    @Test
    void testCompareQueryPerformance() {
        String query = "SELECT department, COUNT(*) as count FROM employees GROUP BY department ORDER BY count DESC";
        PerformanceResult result = fileQueryService.compareQueryPerformance(bucketName, parquetKey, duckdbKey, query);

        assertNotNull(result);
        assertTrue(result.isResultsMatch());
        assertNotNull(result.getParquetQueryTime());
        assertNotNull(result.getDuckdbQueryTime());

        // Log the performance results
        logger.info("Performance comparison results:");
        logger.info("Query: {}", query);
        logger.info("Parquet query time: {}ms", result.getParquetQueryTime().toMillis());
        logger.info("DuckDB query time: {}ms", result.getDuckdbQueryTime().toMillis());
        logger.info("Faster format: {}", result.getFasterFormat());
        logger.info("Speed difference: {}%", String.format("%.2f", result.getSpeedDifferencePercent()));
    }

    @Test
    void testCompareQueryPerformanceMultipleRuns() {
        String query = "SELECT department, COUNT(*) as count FROM employees GROUP BY department ORDER BY count DESC";
        int numRuns = 10;
        PerformanceResult result = fileQueryService.compareQueryPerformance(bucketName, parquetKey, duckdbKey, query, numRuns);

        assertNotNull(result);
        assertTrue(result.isResultsMatch());
        assertEquals(numRuns, result.getNumRuns());

        // Verify that min, max, and avg times are calculated correctly
        assertNotNull(result.getMinParquetQueryTime());
        assertNotNull(result.getMaxParquetQueryTime());
        assertNotNull(result.getAvgParquetQueryTime());
        assertNotNull(result.getMinDuckdbQueryTime());
        assertNotNull(result.getMaxDuckdbQueryTime());
        assertNotNull(result.getAvgDuckdbQueryTime());

        // Verify that the query times lists have the correct size
        assertEquals(numRuns, result.getParquetQueryTimes().size());
        assertEquals(numRuns, result.getDuckdbQueryTimes().size());

        // Log the performance results
        logger.info("Performance comparison results (multiple runs):");
        logger.info("Query: {}", query);
        logger.info("Number of runs: {}", numRuns);
        logger.info("Parquet query time (min/avg/max): {}/{}/{}ms", 
                result.getMinParquetQueryTime().toMillis(),
                result.getAvgParquetQueryTime().toMillis(),
                result.getMaxParquetQueryTime().toMillis());
        logger.info("DuckDB query time (min/avg/max): {}/{}/{}ms", 
                result.getMinDuckdbQueryTime().toMillis(),
                result.getAvgDuckdbQueryTime().toMillis(),
                result.getMaxDuckdbQueryTime().toMillis());
        logger.info("Faster format (based on avg): {}", result.getFasterFormat());
        logger.info("Speed difference (based on avg): {}%", String.format("%.2f", result.getSpeedDifferencePercent()));
    }
}
