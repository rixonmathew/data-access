package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.model.IpcFormat;
import com.rixon.learn.spring.data.arrow.model.Trade;
import com.rixon.learn.spring.data.arrow.service.ArrowColumnarService;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.BufferLedger;
import org.apache.arrow.memory.unsafe.UnsafeAllocationManager;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.util.TransferPair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Arrow's in-memory columnar layout and IPC serialization. */
@ArrowIntegrationTest
class ArrowColumnarIntegrationTest {

    private static final List<Trade> TRADES = TestTrades.generate(10_000);

    @Autowired
    private ArrowColumnarService columnar;

    @Autowired
    private BufferAllocator rootAllocator;

    private BufferAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = rootAllocator.newChildAllocator("columnar-test", 0, Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() {
        assertThat(allocator.getAllocatedMemory()).as("Arrow memory leaked by the test").isZero();
        allocator.close();
    }

    @Test
    void testAllocatorUsesUnsafeAllocationManager() {
        // arrow-memory-netty fails on Java 25 with Boot's Netty 4.2; the module pins the Unsafe manager
        try (ArrowBuf buffer = allocator.buffer(64)) {
            BufferLedger ledger = (BufferLedger) buffer.getReferenceManager();
            assertThat(ledger.getAllocationManager()).isInstanceOf(UnsafeAllocationManager.class);
            assertThat(allocator.getAllocatedMemory()).isGreaterThanOrEqualTo(64);
        }
    }

    @Test
    void testVectorsRoundTripWithNullsTrackedInValidityBitmap() {
        try (VectorSchemaRoot root = columnar.toVectors(TRADES, allocator)) {
            assertThat(root.getRowCount()).isEqualTo(10_000);
            assertThat(root.getSchema()).isEqualTo(ArrowColumnarService.TRADE_SCHEMA);

            VarCharVector venue = (VarCharVector) root.getVector("venue");
            assertThat(venue.getNullCount()).isEqualTo(2_500);     // every 4th trade
            assertThat(venue.isNull(3)).isTrue();                  // trade 4
            assertThat(venue.getObject(0).toString()).isEqualTo("NASDAQ");

            assertThat(columnar.fromVectors(root)).isEqualTo(TRADES);
        }
    }

    @Test
    void testSliceSharesFixedWidthBuffersWithoutCopying() {
        try (VectorSchemaRoot root = columnar.toVectors(TRADES, allocator)) {
            BigIntVector quantity = (BigIntVector) root.getVector("quantity");
            long before = allocator.getAllocatedMemory();

            // Fixed-width column: the slice points into the same buffers, nothing is allocated.
            // The offset is a multiple of 8, so the validity bitmap slices on a byte boundary too.
            TransferPair pair = quantity.getTransferPair(allocator);
            pair.splitAndTransfer(1_024, 500);
            try (BigIntVector sliced = (BigIntVector) pair.getTo()) {
                assertThat(allocator.getAllocatedMemory()).isEqualTo(before);
                assertThat(sliced.getDataBuffer().memoryAddress())
                        .isEqualTo(quantity.getDataBuffer().memoryAddress() + 1_024L * BigIntVector.TYPE_WIDTH);
                assertThat(sliced.get(0)).isEqualTo(TRADES.get(1_024).getQuantity());
            }

            // Whole-row slice: string columns share their value bytes but need new offset buffers rebased
            // to start at 0, so the only allocation is one small offsets buffer per string column
            try (VectorSchemaRoot slice = root.slice(1_024, 500)) {
                long allocated = allocator.getAllocatedMemory() - before;
                assertThat(allocated).isPositive().isLessThanOrEqualTo(3 * 2_048L);   // 3 VARCHAR columns x (501 x 4 bytes, rounded)
                assertThat(columnar.fromVectors(slice)).isEqualTo(TRADES.subList(1_024, 1_524));
            }
        }
    }

    @Test
    void testIpcStreamAndFileFormatsRoundTripInBatches() throws IOException {
        byte[] stream = columnar.writeIpc(TRADES, IpcFormat.STREAM, CompressionUtil.CodecType.NO_COMPRESSION, 2_048, allocator);
        byte[] file = columnar.writeIpc(TRADES, IpcFormat.FILE, CompressionUtil.CodecType.NO_COMPRESSION, 2_048, allocator);

        // The file format is framed by "ARROW1" magic bytes and carries a footer indexing the batches
        assertThat(new String(Arrays.copyOf(file, 6), StandardCharsets.US_ASCII)).isEqualTo("ARROW1");
        assertThat(new String(Arrays.copyOfRange(file, file.length - 6, file.length), StandardCharsets.US_ASCII)).isEqualTo("ARROW1");
        assertThat(new String(Arrays.copyOf(stream, 6), StandardCharsets.US_ASCII)).isNotEqualTo("ARROW1");

        for (byte[] bytes : List.of(stream, file)) {
            IpcFormat format = bytes == stream ? IpcFormat.STREAM : IpcFormat.FILE;
            List<List<Trade>> batches = columnar.readIpcBatches(bytes, format, allocator);
            assertThat(batches).hasSize(5);                                   // 2048 x 4 + 1808
            assertThat(batches.getLast()).hasSize(10_000 - 4 * 2_048);
            assertThat(batches.stream().flatMap(List::stream).toList()).isEqualTo(TRADES);
        }
    }

    @Test
    void testLz4AndZstdCompressionShrinkIpcAndRoundTrip() throws IOException {
        byte[] plain = columnar.writeIpc(TRADES, IpcFormat.STREAM, CompressionUtil.CodecType.NO_COMPRESSION, 10_000, allocator);
        byte[] lz4 = columnar.writeIpc(TRADES, IpcFormat.STREAM, CompressionUtil.CodecType.LZ4_FRAME, 10_000, allocator);
        byte[] zstd = columnar.writeIpc(TRADES, IpcFormat.STREAM, CompressionUtil.CodecType.ZSTD, 10_000, allocator);

        assertThat(lz4.length).isLessThan(plain.length / 2);
        assertThat(zstd.length).isLessThan(plain.length / 2);
        assertThat(columnar.readIpcBatches(lz4, IpcFormat.STREAM, allocator).getFirst()).isEqualTo(TRADES);
        assertThat(columnar.readIpcBatches(zstd, IpcFormat.STREAM, allocator).getFirst()).isEqualTo(TRADES);
    }

    @Test
    void testDictionaryEncodingStoresEachTickerOnce() throws IOException {
        byte[] plain = columnar.writeTickers(TRADES, false, allocator);
        byte[] encoded = columnar.writeTickers(TRADES, true, allocator);

        // 10,000 variable-length strings (4-byte offsets + 4 chars) become 1-byte indices plus a 5-entry dictionary
        assertThat(encoded.length).isLessThan(plain.length * 3 / 4);
        List<String> expected = TRADES.stream().map(Trade::getTicker).toList();
        assertThat(columnar.readTickers(encoded, allocator)).isEqualTo(expected);
        assertThat(columnar.readTickers(plain, allocator)).isEqualTo(expected);
    }
}
