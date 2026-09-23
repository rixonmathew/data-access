package com.rixon.ducklake.ducklake_poc.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for creating and querying partitioned Parquet files using Avro schema.
 */
@Slf4j
@Service
public class PartitionedParquetService {

    @Autowired
    private DuckDBService duckDBService;

    @Autowired(required = false)
    private S3Client s3Client;

    /**
     * Creates sample partitioned Parquet files from an Avro schema.
     *
     * @param schema The Avro schema
     * @param partitionColumns The columns to partition by
     * @param numRecords The number of records to generate
     * @param outputDir The local directory to save the files
     * @param recordGenerator A function to generate records (if null, random data will be generated)
     * @return Map of partition paths to file paths
     * @throws IOException If there's an error creating the files
     */
    public Map<String, List<String>> createPartitionedParquetFiles(
            Schema schema,
            List<String> partitionColumns,
            int numRecords,
            String outputDir,
            Function<Integer, GenericRecord> recordGenerator) throws IOException {

        log.info("Creating {} partitioned Parquet files with schema: {}", numRecords, schema.getName());

        // Create output directory if it doesn't exist
        File outDir = new File(outputDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // Generate records
        List<GenericRecord> records = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            GenericRecord record;
            if (recordGenerator != null) {
                record = recordGenerator.apply(i);
            } else {
                record = generateRandomRecord(schema, i);
            }
            records.add(record);
        }

        // Group records by partition values
        Map<String, List<GenericRecord>> partitionedRecords = partitionRecords(records, partitionColumns);

        // Write each partition to a separate file
        Map<String, List<String>> partitionFiles = new HashMap<>();

        for (Map.Entry<String, List<GenericRecord>> entry : partitionedRecords.entrySet()) {
            String partitionPath = entry.getKey();
            List<GenericRecord> partitionRecords = entry.getValue();

            // Create partition directory
            File partitionDir = new File(outputDir, partitionPath);
            partitionDir.mkdirs();

            // Write records to Parquet file
            String fileName = "part-" + UUID.randomUUID() + ".parquet";
            File outputFile = new File(partitionDir, fileName);

            writeParquetFile(schema, partitionRecords, outputFile.getAbsolutePath());

            // Add file to partition map
            partitionFiles.computeIfAbsent(partitionPath, k -> new ArrayList<>())
                    .add(outputFile.getAbsolutePath());

            log.info("Created Parquet file for partition {}: {}", partitionPath, outputFile.getAbsolutePath());
        }

        return partitionFiles;
    }

    /**
     * Uploads partitioned Parquet files to S3.
     *
     * @param partitionFiles Map of partition paths to file paths
     * @param bucketName The S3 bucket name
     * @param prefix The S3 key prefix
     * @return Map of partition paths to S3 keys
     * @throws IOException If there's an error uploading the files
     */
    public Map<String, List<String>> uploadPartitionedFilesToS3(
            Map<String, List<String>> partitionFiles,
            String bucketName,
            String prefix) throws IOException {

        if (s3Client == null) {
            throw new IllegalStateException("S3Client is not available. Make sure it's properly configured.");
        }

        Map<String, List<String>> s3Keys = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : partitionFiles.entrySet()) {
            String partitionPath = entry.getKey();
            List<String> filePaths = entry.getValue();

            List<String> partitionS3Keys = new ArrayList<>();

            for (String filePath : filePaths) {
                File file = new File(filePath);
                String s3Key = prefix + "/" + partitionPath + "/" + file.getName();

                // Upload file to S3
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Key)
                                .build(),
                        RequestBody.fromFile(file));

                partitionS3Keys.add(s3Key);
                log.info("Uploaded file to S3: s3://{}/{}", bucketName, s3Key);
            }

            s3Keys.put(partitionPath, partitionS3Keys);
        }

        return s3Keys;
    }

    /**
     * Queries partitioned Parquet files using DuckDB.
     *
     * @param tableName The name of the table to create
     * @param s3Path The S3 path to the partitioned Parquet files (e.g., s3://bucket/prefix)
     * @param partitionColumns The columns used for partitioning
     * @param query The SQL query to execute (if null, selects all data)
     * @return The query results
     * @throws SQLException If there's an error executing the query
     */
    public List<Map<String, Object>> queryPartitionedParquetFiles(
            String tableName,
            String s3Path,
            List<String> partitionColumns,
            String query) throws SQLException {

        log.info("Querying partitioned Parquet files at: {}", s3Path);

        try {
            // First, try to list files to verify S3 access
            try {
                List<Map<String, Object>> listResults = duckDBService.executeQuery(
                    "SELECT * FROM s3_list_directories('" + s3Path + "')"
                );
                log.info("S3 directories found: {}", listResults);
            } catch (SQLException e) {
                log.warn("Error listing S3 directories: {}. Will try direct query anyway.", e.getMessage());
            }

            // Create a view over the partitioned Parquet files
            // Use glob pattern to find all parquet files in subdirectories
            String createViewSql = String.format(
                    "CREATE OR REPLACE VIEW %s AS SELECT * FROM parquet_scan('%s/**/*.parquet', hive_partitioning=1)",
                    tableName, s3Path);

            duckDBService.executeStatement(createViewSql);
            log.info("Created view {} over partitioned Parquet files at {}", tableName, s3Path);

            // Execute the query
            if (query == null) {
                query = "SELECT * FROM " + tableName;
            }

            return duckDBService.executeQuery(query);
        } catch (SQLException e) {
            log.error("Error querying partitioned Parquet files: {}", e.getMessage());

            // Try an alternative approach without the glob pattern
            log.info("Trying alternative approach without glob pattern...");
            String altCreateViewSql = String.format(
                    "CREATE OR REPLACE VIEW %s AS SELECT * FROM parquet_scan('%s', hive_partitioning=1)",
                    tableName, s3Path);

            try {
                duckDBService.executeStatement(altCreateViewSql);
                log.info("Created view {} over partitioned Parquet files at {} (alternative approach)", tableName, s3Path);

                if (query == null) {
                    query = "SELECT * FROM " + tableName;
                }

                return duckDBService.executeQuery(query);
            } catch (SQLException ex) {
                log.error("Alternative approach also failed: {}", ex.getMessage());
                throw new SQLException("Failed to query partitioned Parquet files at " + s3Path, e);
            }
        }
    }

    /**
     * Queries partitioned Parquet files from a local directory using DuckDB.
     *
     * @param tableName The name of the table to create
     * @param localDir The local directory containing partitioned Parquet files
     * @param partitionColumns The columns used for partitioning
     * @param query The SQL query to execute (if null, selects all data)
     * @return The query results
     * @throws SQLException If there's an error executing the query
     */
    public List<Map<String, Object>> queryLocalPartitionedParquetFiles(
            String tableName,
            String localDir,
            List<String> partitionColumns,
            String query) throws SQLException {

        log.info("Querying local partitioned Parquet files at: {}", localDir);

        try {
            // Create a view over the partitioned Parquet files
            // Use glob pattern to find all parquet files in subdirectories
            String createViewSql = String.format(
                    "CREATE OR REPLACE VIEW %s AS SELECT * FROM parquet_scan('%s/**/*.parquet', hive_partitioning=1)",
                    tableName, localDir);

            duckDBService.executeStatement(createViewSql);
            log.info("Created view {} over local partitioned Parquet files at {}", tableName, localDir);

            // Execute the query
            if (query == null) {
                query = "SELECT * FROM " + tableName;
            }

            return duckDBService.executeQuery(query);
        } catch (SQLException e) {
            log.error("Error querying local partitioned Parquet files: {}", e.getMessage());

            // Try an alternative approach without the glob pattern
            log.info("Trying alternative approach without glob pattern...");
            String altCreateViewSql = String.format(
                    "CREATE OR REPLACE VIEW %s AS SELECT * FROM parquet_scan('%s', hive_partitioning=1)",
                    tableName, localDir);

            try {
                duckDBService.executeStatement(altCreateViewSql);
                log.info("Created view {} over local partitioned Parquet files at {} (alternative approach)", tableName, localDir);

                if (query == null) {
                    query = "SELECT * FROM " + tableName;
                }

                return duckDBService.executeQuery(query);
            } catch (SQLException ex) {
                log.error("Alternative approach also failed: {}", ex.getMessage());

                // Try one more approach with explicit file listing
                log.info("Trying approach with explicit file listing...");
                try {
                    // List all parquet files in the directory and its subdirectories
                    List<String> parquetFiles = findParquetFiles(localDir);
                    if (parquetFiles.isEmpty()) {
                        throw new SQLException("No Parquet files found in " + localDir);
                    }

                    // Create a table from the first file
                    String firstFile = parquetFiles.get(0);
                    String createTableSql = String.format(
                            "CREATE OR REPLACE VIEW %s AS SELECT * FROM parquet_scan('%s')",
                            tableName, firstFile);
                    duckDBService.executeStatement(createTableSql);
                    log.info("Created view {} from first Parquet file: {}", tableName, firstFile);

                    if (query == null) {
                        query = "SELECT * FROM " + tableName;
                    }

                    return duckDBService.executeQuery(query);
                } catch (Exception listEx) {
                    log.error("All approaches failed: {}", listEx.getMessage());
                    throw new SQLException("Failed to query local partitioned Parquet files at " + localDir, e);
                }
            }
        }
    }

    private List<String> findParquetFiles(String directory) {
        List<String> result = new ArrayList<>();
        File dir = new File(directory);
        findParquetFilesRecursive(dir, result);
        return result;
    }

    private void findParquetFilesRecursive(File dir, List<String> result) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                findParquetFilesRecursive(file, result);
            } else if (file.getName().endsWith(".parquet")) {
                result.add(file.getAbsolutePath());
            }
        }
    }

    // Helper methods

    private GenericRecord generateRandomRecord(Schema schema, int index) {
        GenericRecord record = new GenericData.Record(schema);

        for (Schema.Field field : schema.getFields()) {
            String fieldName = field.name();
            Schema fieldSchema = field.schema();

            // Handle union types (e.g., ["null", "string"])
            if (fieldSchema.getType() == Schema.Type.UNION) {
                fieldSchema = getNonNullSchema(fieldSchema);
            }

            switch (fieldSchema.getType()) {
                case STRING:
                    record.put(fieldName, fieldName + "_" + index);
                    break;
                case INT:
                    record.put(fieldName, index);
                    break;
                case LONG:
                    record.put(fieldName, (long) index);
                    break;
                case FLOAT:
                    record.put(fieldName, index + 0.5f);
                    break;
                case DOUBLE:
                    record.put(fieldName, index + 0.5);
                    break;
                case BOOLEAN:
                    record.put(fieldName, index % 2 == 0);
                    break;
                default:
                    record.put(fieldName, null);
            }
        }

        return record;
    }

    private Schema getNonNullSchema(Schema unionSchema) {
        for (Schema schema : unionSchema.getTypes()) {
            if (schema.getType() != Schema.Type.NULL) {
                return schema;
            }
        }
        return unionSchema.getTypes().get(0);
    }

    private Map<String, List<GenericRecord>> partitionRecords(List<GenericRecord> records, List<String> partitionColumns) {
        return records.stream()
                .collect(Collectors.groupingBy(record -> {
                    StringBuilder partitionPath = new StringBuilder();
                    for (String column : partitionColumns) {
                        Object value = record.get(column);
                        partitionPath.append(column).append("=").append(value).append("/");
                    }
                    return partitionPath.toString();
                }));
    }

    private void writeParquetFile(Schema schema, List<GenericRecord> records, String outputPath) throws IOException {
        Configuration conf = new Configuration();

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new Path(outputPath))
                .withSchema(schema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {

            for (GenericRecord record : records) {
                writer.write(record);
            }
        }
    }

    /**
     * Creates an Avro schema from a JSON schema definition.
     *
     * @param schemaJson The JSON schema definition
     * @return The Avro schema
     */
    public Schema createSchema(String schemaJson) {
        return new Schema.Parser().parse(schemaJson);
    }
}
