package com.rixon.learn.spring.data.iceberg.service;

import com.rixon.learn.spring.data.iceberg.model.IcebergCommitSummary;
import com.rixon.learn.spring.data.iceberg.model.MarketTrade;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IcebergService {

    public static final Schema BASE_TRADE_SCHEMA = new Schema(
            Types.NestedField.required(1, "tradeId", Types.StringType.get()),
            Types.NestedField.required(2, "ticker", Types.StringType.get()),
            Types.NestedField.required(3, "price", Types.DoubleType.get()),
            Types.NestedField.required(4, "quantity", Types.LongType.get()),
            Types.NestedField.required(5, "side", Types.StringType.get()),
            Types.NestedField.required(6, "executedAt", Types.LongType.get())
    );

    private final Configuration hadoopConf;

    public IcebergService() {
        this.hadoopConf = new Configuration();
        this.hadoopConf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
        this.hadoopConf.setBoolean("fs.file.impl.disable.cache", true);
        this.hadoopConf.setBoolean("fs.s3a.impl.disable.cache", true);
    }

    public Configuration getHadoopConf() {
        return hadoopConf;
    }

    public HadoopCatalog getCatalog(String warehouseDir) {
        return new HadoopCatalog(hadoopConf, warehouseDir);
    }

    public Table createOrLoadTable(String warehouseDir, String namespace, String tableName, boolean partitionByTicker) {
        HadoopCatalog catalog = getCatalog(warehouseDir);
        TableIdentifier ident = TableIdentifier.of(namespace, tableName);

        if (catalog.tableExists(ident)) {
            log.info("Loading existing Iceberg table: {}", ident);
            return catalog.loadTable(ident);
        }

        PartitionSpec spec = partitionByTicker
                ? PartitionSpec.builderFor(BASE_TRADE_SCHEMA).identity("ticker").build()
                : PartitionSpec.unpartitioned();

        log.info("Creating new Iceberg table: {} (partitioned: {})", ident, partitionByTicker);
        return catalog.createTable(ident, BASE_TRADE_SCHEMA, spec);
    }

    public IcebergCommitSummary commitTrades(Table table, List<MarketTrade> trades) throws IOException {
        table.refresh();
        var append = table.newAppend();

        if (table.spec().isPartitioned()) {
            // Group trades by partition field (ticker)
            Map<String, List<MarketTrade>> tradesByTicker = trades.stream()
                    .collect(Collectors.groupingBy(MarketTrade::getTicker));

            for (Map.Entry<String, List<MarketTrade>> entry : tradesByTicker.entrySet()) {
                String ticker = entry.getKey();
                List<MarketTrade> partitionTrades = entry.getValue();

                String partitionDir = table.location() + "/data/ticker=" + ticker;
                new File(partitionDir.replace("file:", "")).mkdirs();
                String filePath = partitionDir + "/part-" + UUID.randomUUID() + ".parquet";

                writeParquetFile(table, filePath, partitionTrades);

                PartitionKey partitionKey = new PartitionKey(table.spec(), table.schema());
                GenericRecord sampleRec = createGenericRecord(table.schema(), partitionTrades.getFirst());
                partitionKey.partition(sampleRec);

                DataFile dataFile = DataFiles.builder(table.spec())
                        .withPath(filePath)
                        .withFileSizeInBytes(new File(filePath.replace("file:", "")).length())
                        .withRecordCount(partitionTrades.size())
                        .withFormat(FileFormat.PARQUET)
                        .withPartition(partitionKey)
                        .build();

                append.appendFile(dataFile);
            }
        } else {
            // Unpartitioned table write
            String dataDir = table.location() + "/data";
            new File(dataDir.replace("file:", "")).mkdirs();
            String filePath = dataDir + "/part-" + UUID.randomUUID() + ".parquet";

            writeParquetFile(table, filePath, trades);

            DataFile dataFile = DataFiles.builder(table.spec())
                    .withPath(filePath)
                    .withFileSizeInBytes(new File(filePath.replace("file:", "")).length())
                    .withRecordCount(trades.size())
                    .withFormat(FileFormat.PARQUET)
                    .build();

            append.appendFile(dataFile);
        }

        append.commit();
        table.refresh();

        Snapshot snapshot = table.currentSnapshot();
        log.info("Committed Iceberg snapshot ID: {} (operation: {}, active files: {})",
                snapshot.snapshotId(), snapshot.operation(), snapshot.allManifests(table.io()).size());

        int addedFilesCount = 0;
        for (DataFile ignored : snapshot.addedDataFiles(table.io())) {
            addedFilesCount++;
        }

        return IcebergCommitSummary.builder()
                .snapshotId(snapshot.snapshotId())
                .parentSnapshotId(snapshot.parentId())
                .operation(snapshot.operation())
                .manifestCount(snapshot.allManifests(table.io()).size())
                .dataFilesCount(addedFilesCount)
                .schemaId(table.schema().schemaId())
                .currentSpecId(table.spec().specId())
                .timestampMillis(snapshot.timestampMillis())
                .manifestListLocation(snapshot.manifestListLocation())
                .build();
    }

    public void evolvePartitionSpec(Table table, String fieldName) {
        log.info("Evolving Iceberg partition spec: adding partition field '{}'", fieldName);
        table.updateSpec()
                .addField(fieldName)
                .commit();
        table.refresh();
    }

    public void evolveSchemaAddColumn(Table table, String columnName, Type type, String doc) {
        log.info("Evolving Iceberg schema: adding column '{}' of type {}", columnName, type);
        table.updateSchema()
                .addColumn(columnName, type, doc)
                .commit();
        table.refresh();
    }

    public List<MarketTrade> queryTrades(Table table, Long snapshotId, String tickerFilter) throws IOException {
        table.refresh();
        var reader = IcebergGenerics.read(table);
        if (snapshotId != null) {
            reader.useSnapshot(snapshotId);
        }
        if (tickerFilter != null && !tickerFilter.isBlank()) {
            reader.where(Expressions.equal("ticker", tickerFilter));
        }

        List<MarketTrade> results = new ArrayList<>();
        boolean hasVenue = table.schema().findField("venue") != null;

        try (CloseableIterable<Record> records = reader.build()) {
            for (Record record : records) {
                MarketTrade.MarketTradeBuilder builder = MarketTrade.builder()
                        .tradeId(record.get(0, String.class))
                        .ticker(record.get(1, String.class))
                        .price(record.get(2, Double.class))
                        .quantity(record.get(3, Long.class))
                        .side(record.get(4, String.class))
                        .executedAt(record.get(5, Long.class));

                if (hasVenue && record.size() > 6) {
                    builder.venue(record.get(6, String.class));
                }
                results.add(builder.build());
            }
        }
        return results;
    }

    public List<MarketTrade> queryTradesWithDuckDB(Table table, Long snapshotId) throws SQLException {
        table.refresh();
        var scan = table.newScan();
        if (snapshotId != null) {
            scan = scan.useSnapshot(snapshotId);
        }

        List<String> filePaths = new ArrayList<>();
        for (FileScanTask task : scan.planFiles()) {
            filePaths.add("'" + task.file().path().toString().replace("file:", "") + "'");
        }

        if (filePaths.isEmpty()) {
            return Collections.emptyList();
        }

        List<MarketTrade> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            boolean hasVenue = table.schema().findField("venue") != null;
            String query = hasVenue
                    ? "SELECT tradeId, ticker, price, quantity, side, executedAt, venue FROM read_parquet(" + filePaths + ", union_by_name=true) ORDER BY executedAt ASC"
                    : "SELECT tradeId, ticker, price, quantity, side, executedAt FROM read_parquet(" + filePaths + ", union_by_name=true) ORDER BY executedAt ASC";

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    MarketTrade.MarketTradeBuilder builder = MarketTrade.builder()
                            .tradeId(rs.getString("tradeId"))
                            .ticker(rs.getString("ticker"))
                            .price(rs.getDouble("price"))
                            .quantity(rs.getLong("quantity"))
                            .side(rs.getString("side"))
                            .executedAt(rs.getLong("executedAt"));

                    if (hasVenue) {
                        builder.venue(rs.getString("venue"));
                    }
                    results.add(builder.build());
                }
            }
        }
        return results;
    }

    public Map<String, Object> queryTradeAggregatesWithDuckDB(Table table, Long snapshotId) throws SQLException {
        table.refresh();
        var scan = table.newScan();
        if (snapshotId != null) {
            scan = scan.useSnapshot(snapshotId);
        }

        List<String> filePaths = new ArrayList<>();
        for (FileScanTask task : scan.planFiles()) {
            filePaths.add("'" + task.file().path().toString().replace("file:", "") + "'");
        }

        if (filePaths.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            String query = "SELECT count(*) as total_trades, sum(quantity) as total_volume, round(avg(price), 2) as avg_price FROM read_parquet(" + filePaths + ", union_by_name=true)";

            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    stats.put("totalTrades", rs.getLong("total_trades"));
                    stats.put("totalVolume", rs.getLong("total_volume"));
                    stats.put("avgPrice", rs.getDouble("avg_price"));
                }
            }
        }
        return stats;
    }

    public void rollbackToSnapshot(Table table, long snapshotId) {
        log.info("Rolling back Iceberg table {} to snapshot {}", table.name(), snapshotId);
        table.manageSnapshots()
                .setCurrentSnapshot(snapshotId)
                .commit();
        table.refresh();
    }

    public void syncTableToS3(Table table, S3Client s3Client, String bucketName, String s3Prefix) throws IOException {
        String location = table.location().replace("file:", "");
        File dir = new File(location);
        Path rootPath = dir.toPath();

        try (var stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                String s3Key = s3Prefix.endsWith("/") ? s3Prefix + relativePath : s3Prefix + "/" + relativePath;

                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                s3Client.putObject(putReq, RequestBody.fromFile(path.toFile()));
                log.info("Synced Iceberg Lakehouse file to S3: s3://{}/{}", bucketName, s3Key);
            });
        }
    }

    private void writeParquetFile(Table table, String filePath, List<MarketTrade> trades) throws IOException {
        OutputFile outputFile = table.io().newOutputFile(filePath);
        try (FileAppender<Record> appender = Parquet.write(outputFile)
                .schema(table.schema())
                .createWriterFunc(GenericParquetWriter::buildWriter)
                .build()) {

            for (MarketTrade trade : trades) {
                appender.add(createGenericRecord(table.schema(), trade));
            }
        }
    }

    private GenericRecord createGenericRecord(Schema schema, MarketTrade trade) {
        GenericRecord record = GenericRecord.create(schema);
        record.setField("tradeId", trade.getTradeId());
        record.setField("ticker", trade.getTicker());
        record.setField("price", trade.getPrice());
        record.setField("quantity", trade.getQuantity());
        record.setField("side", trade.getSide());
        record.setField("executedAt", trade.getExecutedAt());

        if (schema.findField("venue") != null) {
            record.setField("venue", trade.getVenue());
        }
        return record;
    }
}
