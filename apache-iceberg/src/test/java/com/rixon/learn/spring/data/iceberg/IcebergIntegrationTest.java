package com.rixon.learn.spring.data.iceberg;

import com.rixon.learn.spring.data.iceberg.model.IcebergCommitSummary;
import com.rixon.learn.spring.data.iceberg.model.MarketTrade;
import com.rixon.learn.spring.data.iceberg.service.IcebergService;
import org.apache.iceberg.Table;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IcebergIntegrationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
            .withServices(S3);

    @Autowired
    private IcebergService icebergService;

    private String warehouseDir;
    private S3Client s3Client;
    private static final String BUCKET_NAME = "lakehouse-iceberg-data";
    private static final String NAMESPACE = "finance";
    private static final String TABLE_NAME = "market_trades";

    @BeforeEach
    void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("iceberg_wh_" + UUID.randomUUID().toString().substring(0, 8));
        warehouseDir = tempDir.toFile().getAbsolutePath();

        s3Client = S3Client.builder()
                .endpointOverride(localStack.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())
                ))
                .region(Region.of(localStack.getRegion()))
                .forcePathStyle(true)
                .build();

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        if (warehouseDir != null) {
            try {
                Files.walk(Path.of(warehouseDir))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Lifecycle: Table creation, ACID commits, hidden partitioning, evolution, time travel, rollback & S3 sync")
    void testCompleteIcebergLakehouseLifecycle() throws Exception {
        // 1. Initial Table Provisioning (Unpartitioned base table)
        Table table = icebergService.createOrLoadTable(warehouseDir, NAMESPACE, TABLE_NAME, false);
        assertThat(table).isNotNull();
        assertThat(table.spec().isUnpartitioned()).isTrue();
        assertThat(table.spec().specId()).isEqualTo(0);
        assertThat(table.schema().columns()).hasSize(6);

        // 2. ACID Transaction Commit 1 (MergeAppend)
        List<MarketTrade> batch1 = List.of(
                MarketTrade.builder().tradeId("T-101").ticker("AAPL").price(220.50).quantity(1000).side("BUY").executedAt(1700000001000L).build(),
                MarketTrade.builder().tradeId("T-102").ticker("NVDA").price(125.00).quantity(500).side("BUY").executedAt(1700000002000L).build(),
                MarketTrade.builder().tradeId("T-103").ticker("MSFT").price(430.00).quantity(200).side("SELL").executedAt(1700000003000L).build()
        );

        IcebergCommitSummary commit1 = icebergService.commitTrades(table, batch1);
        assertThat(commit1.getSnapshotId()).isNotNull();
        assertThat(commit1.getOperation()).isEqualTo("append");
        assertThat(commit1.getDataFilesCount()).isEqualTo(1);
        assertThat(commit1.getCurrentSpecId()).isEqualTo(0);
        assertThat(commit1.getSchemaId()).isEqualTo(0);

        long snapshot1Id = commit1.getSnapshotId();

        // Verify metadata files generated on disk
        File metadataDir = new File(table.location().replace("file:", ""), "metadata");
        assertThat(metadataDir.exists()).isTrue();
        File[] metadataFiles = metadataDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertThat(metadataFiles).isNotNull().isNotEmpty();

        // Verify query results from snapshot 1
        List<MarketTrade> tradesSnap1 = icebergService.queryTrades(table, snapshot1Id, null);
        assertThat(tradesSnap1).hasSize(3);

        // 3. Hidden Partitioning & Dynamic Predicate Pruning
        List<MarketTrade> aaplTrades = icebergService.queryTrades(table, snapshot1Id, "AAPL");
        assertThat(aaplTrades).hasSize(1);
        assertThat(aaplTrades.getFirst().getTicker()).isEqualTo("AAPL");
        assertThat(aaplTrades.getFirst().getPrice()).isEqualTo(220.50);

        // 4. In-Place Partition Evolution: Evolve partition spec to partition by ticker
        icebergService.evolvePartitionSpec(table, "ticker");
        assertThat(table.spec().isPartitioned()).isTrue();
        assertThat(table.spec().specId()).isEqualTo(1);

        // Commit batch 2 under new partition spec (partitioned by ticker)
        List<MarketTrade> batch2 = List.of(
                MarketTrade.builder().tradeId("T-104").ticker("GOOGL").price(180.00).quantity(400).side("BUY").executedAt(1700000004000L).build(),
                MarketTrade.builder().tradeId("T-105").ticker("AMZN").price(195.50).quantity(600).side("SELL").executedAt(1700000005000L).build()
        );

        IcebergCommitSummary commit2 = icebergService.commitTrades(table, batch2);
        long snapshot2Id = commit2.getSnapshotId();
        assertThat(commit2.getCurrentSpecId()).isEqualTo(1);
        assertThat(snapshot2Id).isNotEqualTo(snapshot1Id);

        // Verify that partitioned directory structure exists for new files
        File dataDir = new File(table.location().replace("file:", ""), "data");
        File[] partitionDirs = dataDir.listFiles(File::isDirectory);
        assertThat(partitionDirs).isNotNull().isNotEmpty();

        // Unified query across both unpartitioned and partitioned files
        List<MarketTrade> tradesAfterPartitionEvolution = icebergService.queryTrades(table, null, null);
        assertThat(tradesAfterPartitionEvolution).hasSize(5);

        // 5. In-Place Schema Evolution: Add 'venue' column dynamically
        icebergService.evolveSchemaAddColumn(table, "venue", Types.StringType.get(), "Execution exchange or ATS venue");
        assertThat(table.schema().findField("venue")).isNotNull();

        // Commit batch 3 with populated 'venue' column
        List<MarketTrade> batch3 = List.of(
                MarketTrade.builder().tradeId("T-106").ticker("TSLA").price(250.00).quantity(350).side("BUY").executedAt(1700000006000L).venue("NASDAQ").build(),
                MarketTrade.builder().tradeId("T-107").ticker("JPM").price(210.00).quantity(800).side("BUY").executedAt(1700000007000L).venue("NYSE").build()
        );

        IcebergCommitSummary commit3 = icebergService.commitTrades(table, batch3);
        long snapshot3Id = commit3.getSnapshotId();

        // Verify latest snapshot has all 7 trades, with venue populated or null
        List<MarketTrade> tradesSnap3 = icebergService.queryTrades(table, snapshot3Id, null);
        assertThat(tradesSnap3).hasSize(7);

        MarketTrade tslaTrade = tradesSnap3.stream().filter(t -> "TSLA".equals(t.getTicker())).findFirst().orElseThrow();
        assertThat(tslaTrade.getVenue()).isEqualTo("NASDAQ");

        MarketTrade aaplTradeSnap3 = tradesSnap3.stream().filter(t -> "AAPL".equals(t.getTicker())).findFirst().orElseThrow();
        assertThat(aaplTradeSnap3.getVenue()).isNull();

        // 6. Time Travel Historical Snapshot Queries
        List<MarketTrade> historicalSnap1 = icebergService.queryTrades(table, snapshot1Id, null);
        assertThat(historicalSnap1).hasSize(3);

        List<MarketTrade> historicalSnap2 = icebergService.queryTrades(table, snapshot2Id, null);
        assertThat(historicalSnap2).hasSize(5);

        List<MarketTrade> historicalSnap3 = icebergService.queryTrades(table, snapshot3Id, null);
        assertThat(historicalSnap3).hasSize(7);

        // 7. Embedded DuckDB Vectorized Analytics over Iceberg Parquet files
        List<MarketTrade> duckDbTrades = icebergService.queryTradesWithDuckDB(table, snapshot3Id);
        assertThat(duckDbTrades).hasSize(7);

        Map<String, Object> duckDbAggs = icebergService.queryTradeAggregatesWithDuckDB(table, snapshot3Id);
        assertThat(duckDbAggs.get("totalTrades")).isEqualTo(7L);
        assertThat(duckDbAggs.get("totalVolume")).isEqualTo(3850L); // 1000+500+200+400+600+350+800
        assertThat(duckDbAggs.get("avgPrice")).isNotNull();

        // 8. Snapshot Rollback: Roll back table from snapshot 3 to snapshot 1
        icebergService.rollbackToSnapshot(table, snapshot1Id);
        assertThat(table.currentSnapshot().snapshotId()).isEqualTo(snapshot1Id);

        List<MarketTrade> rolledBackTrades = icebergService.queryTrades(table, null, null);
        assertThat(rolledBackTrades).hasSize(3);

        // 9. LocalStack AWS S3 Cloud Lakehouse Synchronization
        icebergService.syncTableToS3(table, s3Client, BUCKET_NAME, "iceberg-warehouse/" + NAMESPACE + "/" + TABLE_NAME);

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .prefix("iceberg-warehouse/" + NAMESPACE + "/" + TABLE_NAME)
                .build());

        assertThat(listResponse.contents()).isNotEmpty();

        boolean hasMetadataInS3 = listResponse.contents().stream()
                .anyMatch(obj -> obj.key().contains("/metadata/") && obj.key().endsWith(".json"));
        assertThat(hasMetadataInS3).as("S3 bucket should contain Iceberg metadata.json files").isTrue();

        boolean hasParquetInS3 = listResponse.contents().stream()
                .anyMatch(obj -> obj.key().contains("/data/") && obj.key().endsWith(".parquet"));
        assertThat(hasParquetInS3).as("S3 bucket should contain Iceberg Parquet data files").isTrue();
    }
}
