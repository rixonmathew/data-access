package com.rixon.learn.spring.data.ducklake.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.io.LocalOutputFile;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for creating and querying partitioned Parquet files using Avro schema.
 */
@Slf4j
@Service
public class PartitionedParquetService {

    private final DuckDBService duckDBService;
    private final S3Client s3Client;

    public PartitionedParquetService(DuckDBService duckDBService, @Autowired(required = false) S3Client s3Client) {
        this.duckDBService = duckDBService;
        this.s3Client = s3Client;
    }

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
     * Queries Hive-partitioned Parquet files ({@code col=value/} directories) under a local
     * directory or an {@code s3://bucket/prefix}. A view named {@code viewName} is created over
     * all files so partition columns can be filtered on (and pruned) like normal columns.
     *
     * @param viewName The name of the view to create over the files
     * @param rootPath Local directory or S3 prefix containing the partition directories
     * @param query The SQL query to execute (if null, selects all data)
     * @return The query results
     * @throws SQLException If the files cannot be read or the query fails
     */
    public List<Map<String, Object>> queryPartitionedParquetFiles(
            String viewName,
            String rootPath,
            String query) throws SQLException {

        String root = rootPath.endsWith("/") ? rootPath.substring(0, rootPath.length() - 1) : rootPath;
        duckDBService.executeStatement(String.format(
                "CREATE OR REPLACE VIEW %s AS SELECT * FROM read_parquet(%s, hive_partitioning = true)",
                DuckDBService.identifier(viewName), DuckDBService.literal(root + "/**/*.parquet")));
        log.info("Created view {} over partitioned Parquet files at {}", viewName, root);

        return duckDBService.executeQuery(query != null ? query : "SELECT * FROM " + viewName);
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
        // Write through LocalOutputFile (plain java.nio) rather than the Hadoop FileSystem API:
        // Hadoop's UserGroupInformation calls Subject.getSubject(), which throws on Java 24+.
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(
                        new LocalOutputFile(java.nio.file.Path.of(outputPath)))
                .withSchema(schema)
                .withConf(new PlainParquetConfiguration())
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
