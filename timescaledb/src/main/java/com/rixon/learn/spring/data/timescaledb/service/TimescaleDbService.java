package com.rixon.learn.spring.data.timescaledb.service;

import com.rixon.learn.spring.data.timescaledb.model.TimescaleCandle;
import com.rixon.learn.spring.data.timescaledb.model.TimescaleCompressionStat;
import com.rixon.learn.spring.data.timescaledb.model.TimescaleTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

@Service
public class TimescaleDbService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimescaleDbService.class);

    private final JdbcTemplate jdbcTemplate;

    public TimescaleDbService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        LOGGER.info("Initializing TimescaleDB schema & hypertables...");
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            LOGGER.info("TimescaleDB schema initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize TimescaleDB schema: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void truncateTicks() {
        jdbcTemplate.execute("TRUNCATE TABLE market_ticks CASCADE;");
    }

    public void ingestTicks(List<TimescaleTick> ticks) {
        String sql = "INSERT INTO market_ticks (time, symbol, price, volume) VALUES (?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, ticks, ticks.size(), (ps, tick) -> {
            ps.setTimestamp(1, Timestamp.from(tick.getTime()));
            ps.setString(2, tick.getSymbol());
            ps.setDouble(3, tick.getPrice());
            ps.setLong(4, tick.getVolume());
        });
        LOGGER.info("Ingested {} ticks into TimescaleDB hypertable.", ticks.size());
    }

    public void refreshContinuousAggregate() {
        LOGGER.info("Refreshing continuous aggregate 'ohlcv_1m'...");
        jdbcTemplate.execute("CALL refresh_continuous_aggregate('ohlcv_1m', NULL, NULL);");
    }

    public List<TimescaleCandle> getContinuousCandlesticks(String symbol) {
        String sql = "SELECT bucket, symbol, open, high, low, close, volume FROM ohlcv_1m WHERE symbol = ? ORDER BY bucket";
        return jdbcTemplate.query(sql, (rs, rowNum) -> TimescaleCandle.builder()
                .bucket(rs.getTimestamp("bucket").toInstant())
                .symbol(rs.getString("symbol"))
                .open(rs.getDouble("open"))
                .high(rs.getDouble("high"))
                .low(rs.getDouble("low"))
                .close(rs.getDouble("close"))
                .volume(rs.getLong("volume"))
                .build(), symbol);
    }

    private static final java.util.regex.Pattern SAFE_INTERVAL_PATTERN =
            java.util.regex.Pattern.compile("^[0-9]+\\s+(second|minute|hour|day|week|month|year)s?$", java.util.regex.Pattern.CASE_INSENSITIVE);

    public List<TimescaleCandle> getDynamicTimeBucketCandles(String symbol, String interval) {
        if (interval == null || !SAFE_INTERVAL_PATTERN.matcher(interval.trim()).matches()) {
            throw new IllegalArgumentException("Invalid or unsafe interval expression: " + interval);
        }
        String cleanInterval = interval.trim();
        String sql = String.format(
                "SELECT time_bucket(INTERVAL '%s', time) AS bucket, symbol, " +
                "first(price, time) as open, " +
                "max(price) as high, " +
                "min(price) as low, " +
                "last(price, time) as close, " +
                "sum(volume) as volume " +
                "FROM market_ticks " +
                "WHERE symbol = ? " +
                "GROUP BY bucket, symbol " +
                "ORDER BY bucket", cleanInterval);

        return jdbcTemplate.query(sql, (rs, rowNum) -> TimescaleCandle.builder()
                .bucket(rs.getTimestamp("bucket").toInstant())
                .symbol(rs.getString("symbol"))
                .open(rs.getDouble("open"))
                .high(rs.getDouble("high"))
                .low(rs.getDouble("low"))
                .close(rs.getDouble("close"))
                .volume(rs.getLong("volume"))
                .build(), symbol);
    }

    public void enableAndRunCompression() {
        LOGGER.info("Configuring TimescaleDB columnar compression policy...");
        jdbcTemplate.execute("ALTER TABLE market_ticks SET (timescaledb.compress, timescaledb.compress_segmentby = 'symbol', timescaledb.compress_orderby = 'time DESC');");
        jdbcTemplate.execute("SELECT compress_chunk(c.chunk_schema || '.' || c.chunk_name) FROM timescaledb_information.chunks c WHERE c.hypertable_name = 'market_ticks';");
        LOGGER.info("Chunks compressed successfully.");
    }

    public TimescaleCompressionStat getCompressionStats() {
        String sql = "SELECT * FROM hypertable_compression_stats('market_ticks');";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return TimescaleCompressionStat.builder()
                        .hypertableName("market_ticks")
                        .totalChunks(rs.getLong("total_chunks"))
                        .compressedChunks(rs.getLong("number_compressed_chunks"))
                        .uncompressedBytes(rs.getLong("before_compression_total_bytes"))
                        .compressedBytes(rs.getLong("after_compression_total_bytes"))
                        .build();
            }
            return null;
        });
    }
}
