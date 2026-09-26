package com.rixon.learn.spring.data.deltalake;

import com.rixon.learn.spring.data.deltalake.model.DeltaCommitSummary;
import com.rixon.learn.spring.data.deltalake.model.MarketTrade;
import com.rixon.learn.spring.data.deltalake.service.DeltaLakeService;
import io.delta.standalone.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class DeltaLakeIntegrationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
            .withServices(S3);

    @Autowired
    private DeltaLakeService deltaLakeService;

    private String tableDir;
    private S3Client s3Client;
    private static final String BUCKET_NAME = "lakehouse-market-data";

    @BeforeEach
    void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("delta_market_trades_" + UUID.randomUUID().toString().substring(0, 8));
        tableDir = tempDir.toFile().getAbsolutePath();

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
        try {
            Files.walk(Path.of(tableDir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Test 1: ACID initial commit (Version 0) provisions Delta transaction log and Parquet table")
    void testInitialCommit() throws Exception {
        List<MarketTrade> batch1 = List.of(
                MarketTrade.builder().tradeId("T-101").ticker("AAPL").price(220.50).quantity(1000).side("BUY").executedAt(1700000001000L).build(),
                MarketTrade.builder().tradeId("T-102").ticker("NVDA").price(125.00).quantity(500).side("BUY").executedAt(1700000002000L).build(),
                MarketTrade.builder().tradeId("T-103").ticker("MSFT").price(430.00).quantity(200).side("SELL").executedAt(1700000003000L).build()
        );

        DeltaCommitSummary commit0 = deltaLakeService.commitTrades(tableDir, batch1, false);

        assertThat(commit0.getVersion()).isEqualTo(0L);
        assertThat(commit0.getOperation()).isEqualTo("CREATE_TABLE");
        assertThat(commit0.getActiveFilesCount()).isEqualTo(1);

        // Verify _delta_log/00000000000000000000.json was created
        File deltaLogJson = new File(tableDir, "_delta_log/00000000000000000000.json");
        assertThat(deltaLogJson).exists();

        // Query snapshot data
        Snapshot snapshot = deltaLakeService.getLatestSnapshot(tableDir);
        List<MarketTrade> queriedTrades = deltaLakeService.querySnapshotTrades(tableDir, snapshot);

        assertThat(queriedTrades).hasSize(3);
        assertThat(queriedTrades.stream().map(MarketTrade::getTicker).toList())
                .containsExactly("AAPL", "NVDA", "MSFT");
    }

    @Test
    @DisplayName("Test 2 & 3: ACID Appends and Time Travel (Version 0 vs Version 1)")
    void testAppendAndTimeTravel() throws Exception {
        // Batch 1: Initial commit (Version 0)
        List<MarketTrade> batch1 = List.of(
                MarketTrade.builder().tradeId("T-101").ticker("AAPL").price(220.50).quantity(1000).side("BUY").executedAt(1700000001000L).build(),
                MarketTrade.builder().tradeId("T-102").ticker("NVDA").price(125.00).quantity(500).side("BUY").executedAt(1700000002000L).build()
        );
        deltaLakeService.commitTrades(tableDir, batch1, false);

        // Batch 2: Append commit (Version 1)
        List<MarketTrade> batch2 = List.of(
                MarketTrade.builder().tradeId("T-103").ticker("GOOGL").price(180.00).quantity(400).side("BUY").executedAt(1700000003000L).build(),
                MarketTrade.builder().tradeId("T-104").ticker("TSLA").price(250.00).quantity(300).side("SELL").executedAt(1700000004000L).build()
        );
        DeltaCommitSummary commit1 = deltaLakeService.commitTrades(tableDir, batch2, false);

        assertThat(commit1.getVersion()).isEqualTo(1L);
        assertThat(commit1.getOperation()).isEqualTo("WRITE");
        assertThat(commit1.getActiveFilesCount()).isEqualTo(2);

        // --- Time Travel to Version 0 ---
        Snapshot snapshotV0 = deltaLakeService.getTimeTravelSnapshot(tableDir, 0L);
        List<MarketTrade> tradesAtV0 = deltaLakeService.querySnapshotTrades(tableDir, snapshotV0);
        assertThat(tradesAtV0).hasSize(2);
        assertThat(tradesAtV0.stream().map(MarketTrade::getTicker).toList())
                .containsExactly("AAPL", "NVDA");

        // --- Query Latest Snapshot (Version 1) ---
        Snapshot snapshotV1 = deltaLakeService.getLatestSnapshot(tableDir);
        List<MarketTrade> tradesAtV1 = deltaLakeService.querySnapshotTrades(tableDir, snapshotV1);
        assertThat(tradesAtV1).hasSize(4);
        assertThat(tradesAtV1.stream().map(MarketTrade::getTicker).toList())
                .containsExactly("AAPL", "NVDA", "GOOGL", "TSLA");
    }

    @Test
    @DisplayName("Test 4: Schema Evolution adds new 'venue' column dynamically at Version 2")
    void testSchemaEvolution() throws Exception {
        // Version 0: Base 6 columns
        List<MarketTrade> batch1 = List.of(
                MarketTrade.builder().tradeId("T-01").ticker("AAPL").price(220.0).quantity(500).side("BUY").executedAt(1700000001000L).build()
        );
        deltaLakeService.commitTrades(tableDir, batch1, false);

        // Version 1: Evolve schema with 'venue' column
        List<MarketTrade> batch2 = List.of(
                MarketTrade.builder().tradeId("T-02").ticker("NVDA").price(125.0).quantity(300).side("BUY").executedAt(1700000002000L).venue("NASDAQ").build()
        );
        DeltaCommitSummary commit1 = deltaLakeService.commitTrades(tableDir, batch2, true);

        assertThat(commit1.getVersion()).isEqualTo(1L);
        assertThat(commit1.getOperation()).isEqualTo("ADD_COLUMNS");

        // Verify evolved schema in latest snapshot
        Snapshot latestSnapshot = deltaLakeService.getLatestSnapshot(tableDir);
        assertThat(latestSnapshot.getMetadata().getSchema().getFieldNames()).contains("venue");

        // Verify historical V0 schema did NOT have venue
        Snapshot snapshotV0 = deltaLakeService.getTimeTravelSnapshot(tableDir, 0L);
        assertThat(snapshotV0.getMetadata().getSchema().getFieldNames()).doesNotContain("venue");

        // Query latest data: T-01 has null venue, T-02 has NASDAQ venue
        List<MarketTrade> trades = deltaLakeService.querySnapshotTrades(tableDir, latestSnapshot);
        assertThat(trades).hasSize(2);

        MarketTrade trade1 = trades.stream().filter(t -> t.getTradeId().equals("T-01")).findFirst().orElseThrow();
        MarketTrade trade2 = trades.stream().filter(t -> t.getTradeId().equals("T-02")).findFirst().orElseThrow();

        assertThat(trade1.getVenue()).isNull();
        assertThat(trade2.getVenue()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Test 5: LocalStack S3 Lakehouse sync verifies Delta table object hierarchy in S3")
    void testSyncTableToLocalStackS3() throws Exception {
        List<MarketTrade> trades = List.of(
                MarketTrade.builder().tradeId("T-S3-1").ticker("AMD").price(160.0).quantity(150).side("BUY").executedAt(1700000001000L).build()
        );
        deltaLakeService.commitTrades(tableDir, trades, false);

        String s3Prefix = "delta/market_trades";
        deltaLakeService.syncTableToS3(tableDir, s3Client, BUCKET_NAME, s3Prefix);

        // List objects in S3 bucket
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .prefix(s3Prefix)
                .build());

        List<String> keys = listResponse.contents().stream().map(software.amazon.awssdk.services.s3.model.S3Object::key).toList();

        // Assert that both the _delta_log JSON and the Parquet data file are in S3
        assertThat(keys).anyMatch(k -> k.contains("_delta_log/00000000000000000000.json"));
        assertThat(keys).anyMatch(k -> k.endsWith(".parquet"));

        // Verify the commit log file can be inspected via S3 HeadObject
        String logKey = keys.stream().filter(k -> k.contains("_delta_log")).findFirst().orElseThrow();
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET_NAME).key(logKey).build());
        assertThat(head.contentLength()).isGreaterThan(0);
    }
}
