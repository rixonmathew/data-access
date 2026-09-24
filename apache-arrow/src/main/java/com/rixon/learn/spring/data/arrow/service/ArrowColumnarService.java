package com.rixon.learn.spring.data.arrow.service;

import com.rixon.learn.spring.data.arrow.model.IpcFormat;
import com.rixon.learn.spring.data.arrow.model.Trade;
import org.apache.arrow.compression.CommonsCompressionFactory;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionCodec;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.compression.NoCompressionCodec;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.ipc.ArrowWriter;
import org.apache.arrow.vector.ipc.message.IpcOption;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.ByteArrayReadableSeekableByteChannel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arrow's columnar in-memory format and its IPC serialization: trades as column vectors,
 * the STREAM and FILE formats, LZ4/ZSTD buffer compression, and dictionary encoding.
 */
@Service
public class ArrowColumnarService {

    public static final Schema TRADE_SCHEMA = new Schema(List.of(
            Field.notNullable("trade_id", ArrowType.Utf8.INSTANCE),
            Field.notNullable("ticker", ArrowType.Utf8.INSTANCE),
            Field.notNullable("price", new ArrowType.Decimal(12, 2, 128)),
            Field.notNullable("quantity", new ArrowType.Int(64, true)),
            Field.notNullable("trade_date", new ArrowType.Date(DateUnit.DAY)),
            Field.nullable("venue", ArrowType.Utf8.INSTANCE)));

    private static final long TICKER_DICTIONARY_ID = 1L;

    /** CommonsCompressionFactory handles LZ4_FRAME and ZSTD only; uncompressed buffers use the no-op codec. */
    private static final CompressionCodec.Factory CODECS = new CompressionCodec.Factory() {
        @Override
        public CompressionCodec createCodec(CompressionUtil.CodecType codecType) {
            return codecType == CompressionUtil.CodecType.NO_COMPRESSION
                    ? NoCompressionCodec.Factory.INSTANCE.createCodec(codecType)
                    : CommonsCompressionFactory.INSTANCE.createCodec(codecType);
        }

        @Override
        public CompressionCodec createCodec(CompressionUtil.CodecType codecType, int compressionLevel) {
            return codecType == CompressionUtil.CodecType.NO_COMPRESSION
                    ? NoCompressionCodec.Factory.INSTANCE.createCodec(codecType, compressionLevel)
                    : CommonsCompressionFactory.INSTANCE.createCodec(codecType, compressionLevel);
        }
    };

    /** Creates a root holding the trades as columns. The caller closes it. */
    public VectorSchemaRoot toVectors(List<Trade> trades, BufferAllocator allocator) {
        VectorSchemaRoot root = VectorSchemaRoot.create(TRADE_SCHEMA, allocator);
        fill(root, trades);
        return root;
    }

    /** Replaces the contents of a {@link #TRADE_SCHEMA} root with the given trades. */
    public void fill(VectorSchemaRoot root, List<Trade> trades) {
        root.allocateNew();
        VarCharVector tradeId = (VarCharVector) root.getVector("trade_id");
        VarCharVector ticker = (VarCharVector) root.getVector("ticker");
        DecimalVector price = (DecimalVector) root.getVector("price");
        BigIntVector quantity = (BigIntVector) root.getVector("quantity");
        DateDayVector tradeDate = (DateDayVector) root.getVector("trade_date");
        VarCharVector venue = (VarCharVector) root.getVector("venue");
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            tradeId.setSafe(i, utf8(trade.getTradeId()));
            ticker.setSafe(i, utf8(trade.getTicker()));
            price.setSafe(i, trade.getPrice().setScale(2));
            quantity.setSafe(i, trade.getQuantity());
            tradeDate.setSafe(i, (int) trade.getTradeDate().toEpochDay());
            if (trade.getVenue() == null) {
                venue.setNull(i);   // clears the validity bit; no value bytes are written
            } else {
                venue.setSafe(i, utf8(trade.getVenue()));
            }
        }
        root.setRowCount(trades.size());
    }

    public List<Trade> fromVectors(VectorSchemaRoot root) {
        VarCharVector tradeId = (VarCharVector) root.getVector("trade_id");
        VarCharVector ticker = (VarCharVector) root.getVector("ticker");
        DecimalVector price = (DecimalVector) root.getVector("price");
        BigIntVector quantity = (BigIntVector) root.getVector("quantity");
        DateDayVector tradeDate = (DateDayVector) root.getVector("trade_date");
        VarCharVector venue = (VarCharVector) root.getVector("venue");
        List<Trade> trades = new ArrayList<>(root.getRowCount());
        for (int i = 0; i < root.getRowCount(); i++) {
            trades.add(Trade.builder()
                    .tradeId(tradeId.getObject(i).toString())
                    .ticker(ticker.getObject(i).toString())
                    .price(price.getObject(i))
                    .quantity(quantity.get(i))
                    .tradeDate(LocalDate.ofEpochDay(tradeDate.get(i)))
                    .venue(venue.isNull(i) ? null : venue.getObject(i).toString())
                    .build());
        }
        return trades;
    }

    /**
     * Serializes trades in batches of {@code batchSize} rows.
     *
     * @param codec NO_COMPRESSION, LZ4_FRAME or ZSTD; compression applies per buffer inside each batch
     */
    public byte[] writeIpc(List<Trade> trades, IpcFormat format, CompressionUtil.CodecType codec,
                           int batchSize, BufferAllocator allocator) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (VectorSchemaRoot root = VectorSchemaRoot.create(TRADE_SCHEMA, allocator);
             ArrowWriter writer = writer(format, root, null, out, codec)) {
            writer.start();
            for (int from = 0; from < trades.size(); from += batchSize) {
                fill(root, trades.subList(from, Math.min(from + batchSize, trades.size())));
                writer.writeBatch();
            }
            writer.end();
        }
        return out.toByteArray();
    }

    /** Reads IPC bytes back, one list per record batch. Compressed buffers are detected and decompressed. */
    public List<List<Trade>> readIpcBatches(byte[] bytes, IpcFormat format, BufferAllocator allocator) throws IOException {
        List<List<Trade>> batches = new ArrayList<>();
        try (ArrowReader reader = reader(format, bytes, allocator)) {
            while (reader.loadNextBatch()) {
                batches.add(fromVectors(reader.getVectorSchemaRoot()));
            }
        }
        return batches;
    }

    /**
     * Writes the ticker and quantity columns as an IPC stream. With {@code dictionaryEncoded}, each
     * distinct ticker string is sent once in a dictionary batch and the column holds small integer indices.
     */
    public byte[] writeTickers(List<Trade> trades, boolean dictionaryEncoded, BufferAllocator allocator) throws IOException {
        List<String> distinct = trades.stream().map(Trade::getTicker).distinct().sorted().toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (VarCharVector tickers = new VarCharVector("ticker", allocator);
             BigIntVector quantity = new BigIntVector("quantity", allocator);
             VarCharVector dictionaryValues = new VarCharVector("ticker_dictionary", allocator)) {
            tickers.allocateNew(trades.size());
            quantity.allocateNew(trades.size());
            for (int i = 0; i < trades.size(); i++) {
                tickers.setSafe(i, utf8(trades.get(i).getTicker()));
                quantity.set(i, trades.get(i).getQuantity());
            }
            tickers.setValueCount(trades.size());
            quantity.setValueCount(trades.size());

            if (!dictionaryEncoded) {
                try (VectorSchemaRoot root = new VectorSchemaRoot(List.of(tickers, quantity));
                     ArrowWriter writer = writer(IpcFormat.STREAM, root, null, out, CompressionUtil.CodecType.NO_COMPRESSION)) {
                    writer.start();
                    writer.writeBatch();
                    writer.end();
                }
                return out.toByteArray();
            }

            dictionaryValues.allocateNew(distinct.size());
            for (int i = 0; i < distinct.size(); i++) {
                dictionaryValues.setSafe(i, utf8(distinct.get(i)));
            }
            dictionaryValues.setValueCount(distinct.size());
            Dictionary dictionary = new Dictionary(dictionaryValues,
                    new DictionaryEncoding(TICKER_DICTIONARY_ID, false, new ArrowType.Int(8, true)));
            DictionaryProvider.MapDictionaryProvider provider = new DictionaryProvider.MapDictionaryProvider(dictionary);

            try (FieldVector encoded = (FieldVector) DictionaryEncoder.encode(tickers, dictionary);
                 VectorSchemaRoot root = new VectorSchemaRoot(List.of(encoded, quantity));
                 ArrowWriter writer = writer(IpcFormat.STREAM, root, provider, out, CompressionUtil.CodecType.NO_COMPRESSION)) {
                writer.start();
                writer.writeBatch();
                writer.end();
            }
        }
        return out.toByteArray();
    }

    /** Reads {@link #writeTickers} output, decoding dictionary indices back to strings when present. */
    public List<String> readTickers(byte[] bytes, BufferAllocator allocator) throws IOException {
        List<String> tickers = new ArrayList<>();
        try (ArrowStreamReader reader = new ArrowStreamReader(new ByteArrayInputStream(bytes), allocator)) {
            while (reader.loadNextBatch()) {
                FieldVector column = reader.getVectorSchemaRoot().getVector("ticker");
                DictionaryEncoding encoding = column.getField().getDictionary();
                if (encoding == null) {
                    addStrings((VarCharVector) column, tickers);
                    continue;
                }
                Map<Long, Dictionary> dictionaries = reader.getDictionaryVectors();
                try (VarCharVector decoded = (VarCharVector) DictionaryEncoder.decode(column, dictionaries.get(encoding.getId()))) {
                    addStrings(decoded, tickers);
                }
            }
        }
        return tickers;
    }

    private static void addStrings(VarCharVector vector, List<String> out) {
        for (int i = 0; i < vector.getValueCount(); i++) {
            out.add(vector.getObject(i).toString());
        }
    }

    private static ArrowWriter writer(IpcFormat format, VectorSchemaRoot root, DictionaryProvider provider,
                                      ByteArrayOutputStream out, CompressionUtil.CodecType codec) {
        return switch (format) {
            case STREAM -> new ArrowStreamWriter(root, provider, Channels.newChannel(out), IpcOption.DEFAULT,
                    CODECS, codec);
            case FILE -> new ArrowFileWriter(root, provider, Channels.newChannel(out), null, IpcOption.DEFAULT,
                    CODECS, codec);
        };
    }

    private static ArrowReader reader(IpcFormat format, byte[] bytes, BufferAllocator allocator) {
        return switch (format) {
            case STREAM -> new ArrowStreamReader(new ByteArrayInputStream(bytes), allocator, CODECS);
            case FILE -> new ArrowFileReader(new ByteArrayReadableSeekableByteChannel(bytes), allocator,
                    CODECS);
        };
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
