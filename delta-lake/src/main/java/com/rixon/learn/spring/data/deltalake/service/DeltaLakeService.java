package com.rixon.learn.spring.data.deltalake.service;

import com.rixon.learn.spring.data.deltalake.model.DeltaCommitSummary;
import com.rixon.learn.spring.data.deltalake.model.MarketTrade;
import io.delta.standalone.DeltaLog;
import io.delta.standalone.OptimisticTransaction;
import io.delta.standalone.Operation;
import io.delta.standalone.Snapshot;
import io.delta.standalone.actions.Action;
import io.delta.standalone.actions.AddFile;
import io.delta.standalone.actions.CommitInfo;
import io.delta.standalone.actions.Metadata;
import io.delta.standalone.types.DoubleType;
import io.delta.standalone.types.LongType;
import io.delta.standalone.types.StringType;
import io.delta.standalone.types.StructType;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DeltaLakeService {

    private final Configuration hadoopConf;

    public DeltaLakeService() {
        this.hadoopConf = new Configuration();
        this.hadoopConf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
        this.hadoopConf.set("delta.logStore.class", "io.delta.storage.LocalLogStore");
        this.hadoopConf.setBoolean("fs.file.impl.disable.cache", true);
        this.hadoopConf.setBoolean("fs.s3a.impl.disable.cache", true);
    }

    public DeltaCommitSummary commitTrades(String tableDir, List<MarketTrade> trades, boolean evolveSchema) throws Exception {
        new File(tableDir).mkdirs();

        DeltaLog deltaLog = DeltaLog.forTable(hadoopConf, tableDir);
        OptimisticTransaction txn = deltaLog.startTransaction();

        boolean tableExists = deltaLog.tableExists();
        boolean includeVenue = evolveSchema || (tableExists && hasField(deltaLog.snapshot().getMetadata().getSchema(), "venue"));

        // 1. Write the columnar Parquet data file
        String parquetFileName = writeTradesToParquet(tableDir, trades, includeVenue);
        File parquetFile = new File(tableDir, parquetFileName);

        // 2. Build Actions
        List<Action> actions = new ArrayList<>();

        if (!tableExists) {
            StructType baseSchema = new StructType()
                    .add("tradeId", new StringType(), false)
                    .add("ticker", new StringType(), false)
                    .add("price", new DoubleType(), false)
                    .add("quantity", new LongType(), false)
                    .add("side", new StringType(), false)
                    .add("executedAt", new LongType(), false);

            Metadata metadata = Metadata.builder()
                    .schema(baseSchema)
                    .name("market_trades")
                    .description("High-frequency market executions table in Delta Lake format")
                    .build();
            txn.updateMetadata(metadata);
        } else if (evolveSchema && !hasField(deltaLog.snapshot().getMetadata().getSchema(), "venue")) {
            StructType evolvedSchema = new StructType()
                    .add("tradeId", new StringType(), false)
                    .add("ticker", new StringType(), false)
                    .add("price", new DoubleType(), false)
                    .add("quantity", new LongType(), false)
                    .add("side", new StringType(), false)
                    .add("executedAt", new LongType(), false)
                    .add("venue", new StringType(), true);

            Metadata evolvedMetadata = deltaLog.snapshot().getMetadata().copyBuilder()
                    .schema(evolvedSchema)
                    .build();
            txn.updateMetadata(evolvedMetadata);
        }

        // 3. Create AddFile action
        AddFile addFile = new AddFile.Builder(
                parquetFileName,
                Collections.emptyMap(),
                parquetFile.length(),
                System.currentTimeMillis(),
                true
        ).build();

        actions.add(addFile);

        Operation op = !tableExists ? new Operation(Operation.Name.CREATE_TABLE)
                : evolveSchema ? new Operation(Operation.Name.ADD_COLUMNS)
                : new Operation(Operation.Name.WRITE);

        long committedVersion = txn.commit(actions, op, "Spring-Data-Delta-Lake/1.0").getVersion();

        Snapshot snapshot = deltaLog.snapshot();
        log.info("Committed transaction version {} with op {} (active files: {})",
                committedVersion, op.getName(), snapshot.getAllFiles().size());

        return DeltaCommitSummary.builder()
                .version(committedVersion)
                .operation(op.getName().name())
                .activeFilesCount(snapshot.getAllFiles().size())
                .timestamp(System.currentTimeMillis())
                .engineInfo("Spring-Data-Delta-Lake/1.0")
                .build();
    }

    public Snapshot getLatestSnapshot(String tableDir) {
        DeltaLog deltaLog = DeltaLog.forTable(hadoopConf, tableDir);
        return deltaLog.snapshot();
    }

    public Snapshot getTimeTravelSnapshot(String tableDir, long version) {
        DeltaLog deltaLog = DeltaLog.forTable(hadoopConf, tableDir);
        return deltaLog.getSnapshotForVersionAsOf(version);
    }

    public CommitInfo getCommitInfo(String tableDir, long version) {
        DeltaLog deltaLog = DeltaLog.forTable(hadoopConf, tableDir);
        return deltaLog.getCommitInfoAt(version);
    }

    public List<MarketTrade> querySnapshotTrades(String tableDir, Snapshot snapshot) throws SQLException {
        List<AddFile> files = snapshot.getAllFiles();
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder pathsSql = new StringBuilder("[");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) pathsSql.append(", ");
            String fullPath = new File(tableDir, files.get(i).getPath()).getAbsolutePath();
            pathsSql.append("'").append(fullPath).append("'");
        }
        pathsSql.append("]");

        List<MarketTrade> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            boolean hasVenue = hasField(snapshot.getMetadata().getSchema(), "venue");
            String query = hasVenue
                    ? "SELECT tradeId, ticker, price, quantity, side, executedAt, venue FROM read_parquet(" + pathsSql + ", union_by_name=true) ORDER BY executedAt ASC"
                    : "SELECT tradeId, ticker, price, quantity, side, executedAt FROM read_parquet(" + pathsSql + ", union_by_name=true) ORDER BY executedAt ASC";

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

    public void syncTableToS3(String tableDir, S3Client s3Client, String bucketName, String s3Prefix) throws IOException {
        File dir = new File(tableDir);
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
                log.info("Synced Delta Lake file to S3: s3://{}/{}", bucketName, s3Key);
            });
        }
    }

    private String writeTradesToParquet(String tableDir, List<MarketTrade> trades, boolean includeVenue) throws SQLException {
        String fileName = "part-" + UUID.randomUUID().toString() + ".parquet";
        String filePath = new File(tableDir, fileName).getAbsolutePath();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            if (includeVenue) {
                stmt.execute("CREATE TABLE temp_trades (tradeId VARCHAR, ticker VARCHAR, price DOUBLE, quantity BIGINT, side VARCHAR, executedAt BIGINT, venue VARCHAR);");
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO temp_trades VALUES (?, ?, ?, ?, ?, ?, ?);")) {
                    for (MarketTrade t : trades) {
                        ps.setString(1, t.getTradeId());
                        ps.setString(2, t.getTicker());
                        ps.setDouble(3, t.getPrice());
                        ps.setLong(4, t.getQuantity());
                        ps.setString(5, t.getSide());
                        ps.setLong(6, t.getExecutedAt());
                        ps.setString(7, t.getVenue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } else {
                stmt.execute("CREATE TABLE temp_trades (tradeId VARCHAR, ticker VARCHAR, price DOUBLE, quantity BIGINT, side VARCHAR, executedAt BIGINT);");
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO temp_trades VALUES (?, ?, ?, ?, ?, ?);")) {
                    for (MarketTrade t : trades) {
                        ps.setString(1, t.getTradeId());
                        ps.setString(2, t.getTicker());
                        ps.setDouble(3, t.getPrice());
                        ps.setLong(4, t.getQuantity());
                        ps.setString(5, t.getSide());
                        ps.setLong(6, t.getExecutedAt());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            stmt.execute("COPY temp_trades TO '" + filePath + "' (FORMAT PARQUET);");
        }
        return fileName;
    }

    private boolean hasField(StructType schema, String fieldName) {
        if (schema == null) {
            return false;
        }
        for (String name : schema.getFieldNames()) {
            if (name.equalsIgnoreCase(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
