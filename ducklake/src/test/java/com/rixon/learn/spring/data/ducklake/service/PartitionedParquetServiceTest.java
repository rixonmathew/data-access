package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration.DATA_BUCKET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes Hive-partitioned Parquet from Avro records, uploads it to LocalStack S3, and queries it
 * with DuckDB from both local disk and S3, checking that partition filters prune directories.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class PartitionedParquetServiceTest {

    private static final String PRODUCT_SCHEMA = """
            {
              "type": "record",
              "name": "Product",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "name", "type": "string"},
                {"name": "category", "type": "string"},
                {"name": "department", "type": "string"},
                {"name": "price", "type": "double"}
              ]
            }""";
    private static final String VIEW = "partitioned_test_view";

    @Autowired
    private PartitionedParquetService partitionedParquetService;

    @Autowired
    private DuckDBService duckDBService;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() throws SQLException {
        duckDBService.executeStatement("DROP VIEW IF EXISTS " + VIEW);
    }

    @Test
    void testRandomRecordsPartitionedLocallyAndOnS3() throws IOException, SQLException {
        Schema schema = partitionedParquetService.createSchema("""
                {
                  "type": "record",
                  "name": "Customer",
                  "fields": [
                    {"name": "id", "type": "int"},
                    {"name": "region", "type": "string"},
                    {"name": "status", "type": "string"},
                    {"name": "balance", "type": "double"}
                  ]
                }""");
        String outputDir = tempDir.resolve("customers").toString();

        // The default generator gives every record distinct values: 20 records -> 20 partitions
        Map<String, List<String>> partitionFiles = partitionedParquetService.createPartitionedParquetFiles(
                schema, List.of("region", "status"), 20, outputDir, null);
        assertThat(partitionFiles).hasSize(20).containsKey("region=region_0/status=status_0/");

        String groupBy = "SELECT region, status, COUNT(*) AS count FROM " + VIEW + " GROUP BY region, status";
        assertThat(partitionedParquetService.queryPartitionedParquetFiles(VIEW, outputDir, groupBy)).hasSize(20);

        Map<String, List<String>> s3Keys = partitionedParquetService.uploadPartitionedFilesToS3(
                partitionFiles, DATA_BUCKET, "customers");
        assertThat(s3Keys.values().stream().mapToInt(List::size).sum()).isEqualTo(20);

        String s3Path = "s3://" + DATA_BUCKET + "/customers";
        assertThat(partitionedParquetService.queryPartitionedParquetFiles(VIEW, s3Path, groupBy)).hasSize(20);

        List<Map<String, Object>> pruned = partitionedParquetService.queryPartitionedParquetFiles(VIEW, s3Path,
                "SELECT region, status, balance FROM " + VIEW + " WHERE region = 'region_0'");
        assertThat(pruned).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("region", "region_0").containsEntry("status", "status_0");
            assertThat(row.get("balance")).isEqualTo(0.5);
        });
    }

    @Test
    void testCustomRecordGeneratorAndPartitionPruning() throws IOException, SQLException {
        Schema schema = partitionedParquetService.createSchema(PRODUCT_SCHEMA);
        String outputDir = tempDir.resolve("products").toString();

        partitionedParquetService.createPartitionedParquetFiles(schema, List.of("category", "department"), 60, outputDir,
                index -> {
                    GenericRecord record = new GenericData.Record(schema);
                    record.put("id", index);
                    record.put("name", "Product " + index);
                    record.put("category", "category_" + (index % 5));
                    record.put("department", "department_" + (index % 3));
                    record.put("price", 10.0 + index);
                    return record;
                });

        // 5 categories x 3 departments = 15 partitions, 4 records each
        List<Map<String, Object>> all = partitionedParquetService.queryPartitionedParquetFiles(VIEW, outputDir,
                "SELECT category, department, COUNT(*) AS count FROM " + VIEW
                        + " GROUP BY category, department ORDER BY category, department");
        assertThat(all).hasSize(15).allSatisfy(row -> assertThat(row.get("count")).isEqualTo(4L));

        List<Map<String, Object>> pruned = partitionedParquetService.queryPartitionedParquetFiles(VIEW, outputDir,
                "SELECT category, department, COUNT(*) AS count FROM " + VIEW
                        + " WHERE category = 'category_1' GROUP BY category, department");
        assertThat(pruned).hasSize(3).allSatisfy(row -> assertThat(row).containsEntry("category", "category_1"));

        // The filter on the partition column is pushed into the file scan, so only category_1 files are read
        String plan = partitionedParquetService.queryPartitionedParquetFiles(VIEW, outputDir,
                        "EXPLAIN ANALYZE SELECT count(*) FROM " + VIEW + " WHERE category = 'category_1'")
                .getFirst().values().stream().map(String::valueOf).reduce("", String::concat);
        assertThat(plan).contains("Scanning Files: 3/15");
    }
}
