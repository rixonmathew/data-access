package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.model.IpcFormat;
import com.rixon.learn.spring.data.arrow.model.Trade;
import com.rixon.learn.spring.data.arrow.service.ArrowColumnarService;
import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DuckDB and Arrow exchanging columnar data through the Arrow C Data Interface, in both directions. */
@ArrowIntegrationTest
class DuckDbArrowInteropIntegrationTest {

    @Autowired
    private DuckDbArrowService duckDB;

    @Autowired
    private ArrowColumnarService columnar;

    @Autowired
    private BufferAllocator rootAllocator;

    private BufferAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = rootAllocator.newChildAllocator("interop-test", 0, Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() {
        assertThat(allocator.getAllocatedMemory()).as("Arrow memory leaked by the test").isZero();
        allocator.close();
    }

    @Test
    void testDuckDbQueryExportsArrowBatches() throws Exception {
        long rows = 0;
        long quantity = 0;
        int batches = 0;
        try (DuckDbArrowService.ArrowQuery query = duckDB.query("SELECT * FROM trades", allocator)) {
            VectorSchemaRoot root = query.root();
            // DuckDB's SQL types map onto the same Arrow types the Java side uses
            List<Field> expected = ArrowColumnarService.TRADE_SCHEMA.getFields();
            assertThat(root.getSchema().getFields()).extracting(Field::getName)
                    .containsExactlyElementsOf(expected.stream().map(Field::getName).toList());
            assertThat(root.getSchema().getFields()).extracting(Field::getType)
                    .containsExactlyElementsOf(expected.stream().map(Field::getType).toList());
            while (query.reader().loadNextBatch()) {
                batches++;
                rows += root.getRowCount();
                BigIntVector q = (BigIntVector) root.getVector("quantity");
                for (int i = 0; i < root.getRowCount(); i++) {
                    quantity += q.get(i);
                }
                assertThat(root.getRowCount()).isLessThanOrEqualTo(8_192);
            }
        }
        assertThat(rows).isEqualTo(ArrowIntegrationTest.SAMPLE_ROWS);
        assertThat(batches).isEqualTo((ArrowIntegrationTest.SAMPLE_ROWS + 8_191) / 8_192);   // 7 batches of up to 8192 rows
        assertThat(quantity).isEqualTo(jdbcLong("SELECT sum(quantity) FROM trades"));
    }

    @Test
    void testArrowStreamQueriedBySqlInDuckDb() throws Exception {
        List<Trade> trades = TestTrades.generate(20_000);
        byte[] ipc = columnar.writeIpc(trades, IpcFormat.STREAM, CompressionUtil.CodecType.NO_COMPRESSION, 4_096, allocator);

        // Java-side Arrow batches (read from IPC) are scanned by DuckDB SQL without converting rows
        Map<String, BigDecimal> notionalByTicker = duckDB.withArrowView("arrow_trades",
                new ArrowStreamReader(new ByteArrayInputStream(ipc), allocator), allocator, conn -> {
                    Map<String, BigDecimal> result = new TreeMap<>();
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(
                                 "SELECT ticker, sum(price * quantity) FROM arrow_trades GROUP BY ticker")) {
                        while (rs.next()) {
                            result.put(rs.getString(1), rs.getBigDecimal(2));
                        }
                    }
                    return result;
                });

        Map<String, BigDecimal> expected = trades.stream().collect(Collectors.groupingBy(Trade::getTicker, TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())), BigDecimal::add)));
        assertThat(notionalByTicker).hasSize(5);
        notionalByTicker.forEach((ticker, notional) -> assertThat(notional).isEqualByComparingTo(expected.get(ticker)));
    }

    @Test
    void testArrowViewCanBeScannedOnlyOnce() throws Exception {
        byte[] ipc = columnar.writeIpc(TestTrades.generate(1_000), IpcFormat.STREAM,
                CompressionUtil.CodecType.NO_COMPRESSION, 1_000, allocator);

        duckDB.withArrowView("arrow_once", new ArrowStreamReader(new ByteArrayInputStream(ipc), allocator), allocator, conn -> {
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT count(*) FROM arrow_once")) {
                rs.next();
                assertThat(rs.getLong(1)).isEqualTo(1_000);
            }
            // The stream was consumed by the first scan
            assertThatThrownBy(() -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT count(*) FROM arrow_once");
                }
            }).isInstanceOf(SQLException.class);
            return null;
        });
    }

    @Test
    void testUnreadArrowViewIsReleasedWithoutLeaking() throws Exception {
        byte[] ipc = columnar.writeIpc(TestTrades.generate(1_000), IpcFormat.STREAM,
                CompressionUtil.CodecType.NO_COMPRESSION, 1_000, allocator);

        // Registered but never scanned: the service releases the stream itself (checked by tearDown)
        String result = duckDB.withArrowView("arrow_unread",
                new ArrowStreamReader(new ByteArrayInputStream(ipc), allocator), allocator, conn -> "not scanned");
        assertThat(result).isEqualTo("not scanned");
    }

    private long jdbcLong(String sql) throws SQLException {
        try (Connection conn = duckDB.openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
