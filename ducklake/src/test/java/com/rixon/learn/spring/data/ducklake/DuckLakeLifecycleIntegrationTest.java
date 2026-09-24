package com.rixon.learn.spring.data.ducklake;

import com.rixon.learn.spring.data.ducklake.model.CompactionResult;
import com.rixon.learn.spring.data.ducklake.model.DuckLakeDataFile;
import com.rixon.learn.spring.data.ducklake.model.DuckLakeSnapshot;
import com.rixon.learn.spring.data.ducklake.model.Trade;
import com.rixon.learn.spring.data.ducklake.model.TradeChange;
import com.rixon.learn.spring.data.ducklake.service.DuckLakeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * End-to-end DuckLake lifecycle against a PostgreSQL catalog with data files on LocalStack S3.
 * <p>
 * Snapshot ids are global to the catalog, so assertions compare against ids captured during the
 * test rather than absolute values. Snapshot expiry is catalog-wide; this is the only test class
 * that relies on historical snapshots.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DuckLakeLifecycleIntegrationTest {

    private static final String TABLE = "trades_lifecycle";
    private static final String INLINED_TABLE = "trades_inlined";

    @Autowired
    private DuckLakeService duckLake;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @Order(1)
    @DisplayName("Lifecycle: ACID commits, rollback, time travel, change feed, schema & partition evolution, compaction, expiry, cleanup")
    void testCompleteDuckLakeLifecycle() throws Exception {
        // 1. Create a table partitioned by ticker. Creating it again is a no-op.
        assertThat(duckLake.createTradesTable(TABLE, List.of("ticker"))).isTrue();
        assertThat(duckLake.createTradesTable(TABLE, List.of("ticker"))).isFalse();
        long createdSnapshot = duckLake.currentSnapshotId();
        assertThat(duckLake.findTrades(TABLE, null)).isEmpty();

        // 2. One commit of three rows is one snapshot, and writes one Parquet file per partition to S3
        long s1 = duckLake.appendTrades(TABLE, List.of(
                trade("T-101", "AAPL", "220.50", 100, "2026-01-05"),
                trade("T-102", "NVDA", "125.00", 50, "2026-01-05"),
                trade("T-103", "MSFT", "430.00", 20, "2026-01-06")));
        assertThat(s1).isEqualTo(createdSnapshot + 1);

        List<DuckLakeDataFile> files = duckLake.listDataFiles(TABLE);
        assertThat(files).hasSize(3);
        assertThat(files).allSatisfy(f ->
                assertThat(f.getDataFile()).startsWith("s3://" + TestcontainersConfiguration.LAKE_BUCKET + "/"));
        assertThat(files).extracting(DuckLakeDataFile::getDataFile)
                .anySatisfy(p -> assertThat(p).contains("/ticker=AAPL/"))
                .anySatisfy(p -> assertThat(p).contains("/ticker=NVDA/"))
                .anySatisfy(p -> assertThat(p).contains("/ticker=MSFT/"));
        assertThat(tableObjectsInS3(TABLE)).hasSize(3);

        // 3. Two more small AAPL commits -> two more snapshots and two more AAPL files (compacted later)
        long s2 = duckLake.appendTrades(TABLE, List.of(trade("T-104", "AAPL", "221.00", 10, "2026-01-07")));
        long s3 = duckLake.appendTrades(TABLE, List.of(trade("T-105", "AAPL", "222.10", 15, "2026-01-08")));
        assertThat(s2).isEqualTo(s1 + 1);
        assertThat(s3).isEqualTo(s2 + 1);
        assertThat(aaplFiles()).hasSize(3);

        // 4. Multi-statement transaction: a price correction and a new trade commit as ONE snapshot
        long s4 = duckLake.commitCorrections(TABLE,
                Map.of("T-102", new BigDecimal("126.50")),
                List.of(trade("T-106", "GOOG", "180.00", 30, "2026-01-08")));
        assertThat(s4).isEqualTo(s3 + 1);
        assertThat(duckLake.tradeChanges(TABLE, s4, s4))
                .extracting(TradeChange::getChangeType, TradeChange::getTradeId, TradeChange::getPrice)
                .containsExactlyInAnyOrder(
                        tuple("update_preimage", "T-102", new BigDecimal("125.00")),
                        tuple("update_postimage", "T-102", new BigDecimal("126.50")),
                        tuple("insert", "T-106", new BigDecimal("180.00")));

        // 5. Failed transactions leave no trace: no snapshot, no rows
        assertThatThrownBy(() -> duckLake.appendTrades(TABLE, List.of(
                trade("T-107", "AAPL", "223.00", 5, "2026-01-09"),
                trade("T-108", null, "99.00", 5, "2026-01-09"))))   // ticker is NOT NULL
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("NOT NULL");
        assertThatThrownBy(() -> duckLake.commitCorrections(TABLE,
                Map.of("T-999", new BigDecimal("1.00")),                    // unknown trade -> rollback
                List.of(trade("T-109", "AAPL", "224.00", 5, "2026-01-09"))))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("T-999");
        assertThat(duckLake.currentSnapshotId()).isEqualTo(s4);
        assertThat(duckLake.findTrades(TABLE, null)).extracting(Trade::getTradeId)
                .containsExactly("T-101", "T-102", "T-103", "T-104", "T-105", "T-106");

        // 6. Time travel by version and by timestamp
        assertThat(duckLake.findTrades(TABLE, createdSnapshot)).isEmpty();
        List<Trade> atS1 = duckLake.findTrades(TABLE, s1);
        assertThat(atS1).extracting(Trade::getTradeId).containsExactly("T-101", "T-102", "T-103");
        assertThat(atS1.get(1).getPrice()).isEqualByComparingTo("125.00");
        assertThat(duckLake.findTrades(TABLE, null).get(1).getPrice()).isEqualByComparingTo("126.50");

        OffsetDateTime s1Time = snapshot(s1).getSnapshotTime();
        assertThat(duckLake.findTradesAsOf(TABLE, s1Time)).extracting(Trade::getTradeId)
                .containsExactly("T-101", "T-102", "T-103");

        // 7. Schema evolution: add a column without rewriting data; history keeps the old schema
        assertThat(duckLake.columns(TABLE)).containsExactly("trade_id", "ticker", "price", "quantity", "trade_date");
        long evolved = duckLake.addColumn(TABLE, "venue", "VARCHAR");
        assertThat(snapshot(evolved).getSchemaVersion()).isGreaterThan(snapshot(s4).getSchemaVersion());
        assertThat(duckLake.columns(TABLE)).endsWith("venue");
        assertThat(duckLake.columns(TABLE, s4)).doesNotContain("venue");

        long s5 = duckLake.appendTrades(TABLE, List.of(trade("T-110", "NVDA", "127.00", 40, "2026-01-12", "NASDAQ")));
        List<Trade> current = duckLake.findTrades(TABLE, null);
        assertThat(current).filteredOn(t -> t.getTradeId().equals("T-101")).singleElement()
                .extracting(Trade::getVenue).isNull();
        assertThat(current).filteredOn(t -> t.getTradeId().equals("T-110")).singleElement()
                .extracting(Trade::getVenue).isEqualTo("NASDAQ");

        // 8. Partition evolution: new writes use year/month; existing files keep ticker=... layout
        List<String> pathsBefore = duckLake.listDataFiles(TABLE).stream().map(DuckLakeDataFile::getDataFile).toList();
        assertThat(pathsBefore).allSatisfy(p -> assertThat(p).contains("/ticker="));
        duckLake.setPartitioning(TABLE, List.of("year(trade_date)", "month(trade_date)"));
        long s6 = duckLake.appendTrades(TABLE, List.of(trade("T-111", "MSFT", "431.00", 25, "2026-02-10", "NYSE")));
        assertThat(s6).isGreaterThan(s5);
        List<String> paths = duckLake.listDataFiles(TABLE).stream().map(DuckLakeDataFile::getDataFile).toList();
        assertThat(paths).containsAll(pathsBefore);
        assertThat(paths).hasSize(pathsBefore.size() + 1)
                .filteredOn(p -> p.contains("/year=2026/month=2/")).hasSize(1);
        assertThat(duckLake.findTrades(TABLE, null)).hasSize(8);

        // 9. Compaction merges the three small AAPL files; data is unchanged and history still readable
        int filesBeforeCompaction = duckLake.listDataFiles(TABLE).size();
        CompactionResult compaction = duckLake.compact(TABLE);
        assertThat(compaction.getFilesProcessed()).isGreaterThanOrEqualTo(3);
        assertThat(compaction.getFilesCreated()).isGreaterThanOrEqualTo(1);
        assertThat(duckLake.listDataFiles(TABLE)).hasSizeLessThan(filesBeforeCompaction);
        assertThat(aaplFiles()).hasSize(1);
        assertThat(duckLake.findTrades(TABLE, null)).hasSize(8);
        assertThat(duckLake.findTrades(TABLE, s1)).hasSize(3);

        // 10. Expire old snapshots, then delete the files only they referenced
        int objectsBeforeCleanup = tableObjectsInS3(TABLE).size();
        List<Long> expired = duckLake.expireSnapshots(OffsetDateTime.now());
        assertThat(expired).contains(s1, s2, s3);
        List<String> deleted = duckLake.cleanupOldFiles();
        assertThat(deleted).filteredOn(p -> p.contains("/" + TABLE + "/ticker=AAPL/")).hasSize(3);
        assertThat(tableObjectsInS3(TABLE)).hasSize(objectsBeforeCleanup - (int) deleted.stream()
                .filter(p -> p.contains("/" + TABLE + "/")).count());
        assertThat(duckLake.findTrades(TABLE, null)).hasSize(8);
        assertThatThrownBy(() -> duckLake.findTrades(TABLE, s1))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("No snapshot found");

        // 11. All of this metadata lives in PostgreSQL
        List<DuckLakeSnapshot> snapshots = duckLake.listSnapshots();
        try (Connection pg = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(pgCount(pg, "SELECT count(*) FROM ducklake_snapshot")).isEqualTo(snapshots.size());
            assertThat(pgCount(pg, "SELECT count(*) FROM ducklake_table WHERE table_name = ? AND end_snapshot IS NULL", TABLE))
                    .isEqualTo(1);
            assertThat(pgCount(pg, """
                    SELECT count(*) FROM ducklake_data_file f
                    JOIN ducklake_table t ON t.table_id = f.table_id AND t.end_snapshot IS NULL
                    WHERE t.table_name = ? AND f.end_snapshot IS NULL""", TABLE))
                    .isEqualTo(duckLake.listDataFiles(TABLE).size());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Data inlining: small inserts stay in the PostgreSQL catalog until flushed to Parquet on S3")
    void testDataInliningAndFlush() throws Exception {
        duckLake.createTradesTable(INLINED_TABLE, List.of());
        duckLake.setDataInliningRowLimit(INLINED_TABLE, 100);

        duckLake.appendTrades(INLINED_TABLE, List.of(
                trade("I-1", "AAPL", "220.00", 1, "2026-03-01"),
                trade("I-2", "AAPL", "221.00", 2, "2026-03-01")));

        // Readable immediately, but no Parquet file exists yet: the rows sit in the catalog database
        assertThat(duckLake.findTrades(INLINED_TABLE, null)).hasSize(2);
        assertThat(duckLake.listDataFiles(INLINED_TABLE)).isEmpty();
        assertThat(tableObjectsInS3(INLINED_TABLE)).isEmpty();

        assertThat(duckLake.flushInlinedData(INLINED_TABLE)).isEqualTo(2);
        assertThat(duckLake.listDataFiles(INLINED_TABLE)).hasSize(1);
        assertThat(tableObjectsInS3(INLINED_TABLE)).hasSize(1);
        assertThat(duckLake.findTrades(INLINED_TABLE, null)).extracting(Trade::getTradeId).containsExactly("I-1", "I-2");
    }

    private List<String> aaplFiles() throws SQLException {
        return duckLake.listDataFiles(TABLE).stream()
                .map(DuckLakeDataFile::getDataFile)
                .filter(p -> p.contains("/ticker=AAPL/"))
                .toList();
    }

    private DuckLakeSnapshot snapshot(long id) throws SQLException {
        return duckLake.listSnapshots().stream()
                .filter(s -> s.getSnapshotId() == id)
                .findFirst()
                .orElseThrow();
    }

    private List<S3Object> tableObjectsInS3(String table) {
        return s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(TestcontainersConfiguration.LAKE_BUCKET)
                        .prefix(TestcontainersConfiguration.LAKE_PREFIX + "main/" + table + "/")
                        .build())
                .contents().stream().toList();
    }

    private static long pgCount(Connection pg, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = pg.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Trade trade(String id, String ticker, String price, long quantity, String date) {
        return trade(id, ticker, price, quantity, date, null);
    }

    private static Trade trade(String id, String ticker, String price, long quantity, String date, String venue) {
        return Trade.builder()
                .tradeId(id)
                .ticker(ticker)
                .price(new BigDecimal(price))
                .quantity(quantity)
                .tradeDate(LocalDate.parse(date))
                .venue(venue)
                .build();
    }
}
