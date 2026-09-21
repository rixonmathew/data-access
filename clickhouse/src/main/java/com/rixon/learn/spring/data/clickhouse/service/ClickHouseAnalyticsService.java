package com.rixon.learn.spring.data.clickhouse.service;

import com.rixon.learn.spring.data.clickhouse.dto.MarketTickRow;
import com.rixon.learn.spring.data.clickhouse.dto.OhlcvBarDto;
import com.rixon.learn.spring.data.clickhouse.dto.TickerMetricsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickHouseAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public void initSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS market_ticks (
                ticker LowCardinality(String),
                event_time DateTime64(3),
                bid Float64,
                ask Float64,
                last_price Float64,
                volume UInt64,
                latency_micros UInt32
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(event_time)
            ORDER BY (ticker, event_time)
        """);
    }

    public void truncateTicks() {
        jdbcTemplate.execute("TRUNCATE TABLE IF EXISTS market_ticks");
    }

    public int[][] batchInsertTicks(List<MarketTickRow> ticks) {
        String sql = """
            INSERT INTO market_ticks (ticker, event_time, bid, ask, last_price, volume, latency_micros)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        return jdbcTemplate.batchUpdate(sql, ticks, ticks.size(),
                (PreparedStatement ps, MarketTickRow tick) -> {
                    ps.setString(1, tick.getTicker());
                    ps.setTimestamp(2, tick.getEventTime());
                    ps.setDouble(3, tick.getBid());
                    ps.setDouble(4, tick.getAsk());
                    ps.setDouble(5, tick.getLastPrice());
                    ps.setLong(6, tick.getVolume());
                    ps.setInt(7, tick.getLatencyMicros());
                });
    }

    public List<TickerMetricsDto> calculateTickerMetrics() {
        String sql = """
            SELECT
                ticker,
                count() AS tick_count,
                avg(last_price) AS avg_price,
                min(last_price) AS min_price,
                max(last_price) AS max_price,
                sum(volume) AS total_volume,
                quantile(0.50)(latency_micros) AS p50_latency,
                quantile(0.95)(latency_micros) AS p95_latency,
                quantile(0.99)(latency_micros) AS p99_latency
            FROM market_ticks
            GROUP BY ticker
            ORDER BY ticker
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> TickerMetricsDto.builder()
                .ticker(rs.getString("ticker"))
                .tickCount(rs.getLong("tick_count"))
                .avgPrice(rs.getDouble("avg_price"))
                .minPrice(rs.getDouble("min_price"))
                .maxPrice(rs.getDouble("max_price"))
                .totalVolume(rs.getLong("total_volume"))
                .p50Latency(rs.getDouble("p50_latency"))
                .p95Latency(rs.getDouble("p95_latency"))
                .p99Latency(rs.getDouble("p99_latency"))
                .build()
        );
    }

    public List<OhlcvBarDto> calculateOhlcvBars(String ticker, int intervalMinutes) {
        String sql = """
            SELECT
                ticker,
                toStartOfInterval(event_time, toIntervalMinute(?)) AS bar_time,
                argMin(last_price, event_time) AS open,
                max(last_price) AS high,
                min(last_price) AS low,
                argMax(last_price, event_time) AS close,
                sum(volume) AS volume
            FROM market_ticks
            WHERE ticker = ?
            GROUP BY ticker, bar_time
            ORDER BY bar_time ASC
        """;

        return jdbcTemplate.query(sql, ps -> {
            ps.setInt(1, intervalMinutes);
            ps.setString(2, ticker);
        }, (rs, rowNum) -> OhlcvBarDto.builder()
                .ticker(rs.getString("ticker"))
                .barTime(rs.getTimestamp("bar_time"))
                .open(rs.getDouble("open"))
                .high(rs.getDouble("high"))
                .low(rs.getDouble("low"))
                .close(rs.getDouble("close"))
                .volume(rs.getLong("volume"))
                .build()
        );
    }
}
