package com.rixon.learn.spring.data.questdb.service;

import com.rixon.learn.spring.data.questdb.model.CandleStick;
import com.rixon.learn.spring.data.questdb.model.MarketTick;
import com.rixon.learn.spring.data.questdb.model.TradeQuoteMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class QuestDbAnalyticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDbAnalyticsService.class);

    private final JdbcTemplate jdbcTemplate;

    public QuestDbAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Demonstrates QuestDB's 'LATEST ON <timestamp> PARTITION BY <symbol>'.
     * Instantly resolves the most recent quote for every symbol without scanning the full table.
     */
    public List<MarketTick> getLatestQuotes() {
        String sql = "SELECT symbol, bid, ask, last_price, volume, timestamp " +
                     "FROM market_quotes " +
                     "LATEST ON timestamp PARTITION BY symbol " +
                     "ORDER BY symbol;";

        LOGGER.info("Executing QuestDB LATEST ON query...");
        return jdbcTemplate.query(sql, (rs, rowNum) -> MarketTick.builder()
                .symbol(rs.getString("symbol"))
                .bid(rs.getDouble("bid"))
                .ask(rs.getDouble("ask"))
                .lastPrice(rs.getDouble("last_price"))
                .volume(rs.getLong("volume"))
                .timestamp(rs.getTimestamp("timestamp").toInstant())
                .build());
    }

    private static final java.util.regex.Pattern SAFE_SYMBOL_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final java.util.regex.Pattern SAFE_INTERVAL_PATTERN = java.util.regex.Pattern.compile("^[0-9]+[smhd]$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Demonstrates QuestDB's 'SAMPLE BY <interval> ALIGN TO CALENDAR'.
     * Creates OHLCV candlestick aggregations in a single vectorized SQL query with first/max/min/last.
     */
    public List<CandleStick> generateCandlesticks(String symbol, String sampleInterval) {
        if (symbol == null || !SAFE_SYMBOL_PATTERN.matcher(symbol.trim()).matches()) {
            throw new IllegalArgumentException("Invalid or unsafe symbol identifier: " + symbol);
        }
        if (sampleInterval == null || !SAFE_INTERVAL_PATTERN.matcher(sampleInterval.trim()).matches()) {
            throw new IllegalArgumentException("Invalid sampleInterval (must match e.g. '1s', '5m', '1h', '1d'): " + sampleInterval);
        }
        String cleanSymbol = symbol.trim();
        String cleanInterval = sampleInterval.trim();

        String sql = String.format(
                "SELECT timestamp, " +
                "first(last_price) as open, " +
                "max(last_price) as high, " +
                "min(last_price) as low, " +
                "last(last_price) as close, " +
                "sum(volume) as volume, " +
                "sum(last_price * volume) / sum(volume) as vwap " +
                "FROM market_quotes " +
                "WHERE symbol = '%s' " +
                "SAMPLE BY %s;", cleanSymbol, cleanInterval);

        LOGGER.info("Executing QuestDB SAMPLE BY {} query for symbol '{}'...", cleanInterval, cleanSymbol);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("timestamp");
            return CandleStick.builder()
                    .timestamp(ts != null ? ts.toInstant() : null)
                    .symbol(cleanSymbol)
                    .open(rs.getDouble("open"))
                    .high(rs.getDouble("high"))
                    .low(rs.getDouble("low"))
                    .close(rs.getDouble("close"))
                    .volume(rs.getLong("volume"))
                    .vwap(rs.getDouble("vwap"))
                    .build();
        });
    }

    /**
     * Demonstrates QuestDB's 'ASOF JOIN'.
     * In algorithmic trading, trades must be reconciled with the prevailing market bid/ask
     * active immediately BEFORE or AT the trade timestamp.
     * QuestDB performs this out-of-order alignment natively in C++ / Assembly.
     */
    public List<TradeQuoteMatch> matchTradesWithQuotesAsof(String symbol) {
        if (symbol == null || !SAFE_SYMBOL_PATTERN.matcher(symbol.trim()).matches()) {
            throw new IllegalArgumentException("Invalid or unsafe symbol identifier: " + symbol);
        }
        String cleanSymbol = symbol.trim();

        String sql = String.format(
                "SELECT t.trade_id, t.symbol, " +
                "t.price as trade_price, t.quantity as trade_quantity, " +
                "q.bid as bid_price, q.ask as ask_price, " +
                "(q.ask - q.bid) as spread, " +
                "t.timestamp as trade_time, q.timestamp as quote_time " +
                "FROM trade_executions t " +
                "ASOF JOIN market_quotes q ON (symbol) " +
                "WHERE t.symbol = '%s' " +
                "ORDER BY t.timestamp;", cleanSymbol);

        LOGGER.info("Executing QuestDB ASOF JOIN for symbol '{}'...", cleanSymbol);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant tradeTime = rs.getTimestamp("trade_time").toInstant();
            Timestamp qts = rs.getTimestamp("quote_time");
            Instant quoteTime = qts != null ? qts.toInstant() : tradeTime;
            long latencyMicros = Math.abs(Duration.between(quoteTime, tradeTime).toNanos()) / 1_000L;

            return TradeQuoteMatch.builder()
                    .tradeId(rs.getString("trade_id"))
                    .symbol(rs.getString("symbol"))
                    .tradePrice(rs.getDouble("trade_price"))
                    .tradeQuantity(rs.getLong("trade_quantity"))
                    .bidPrice(rs.getDouble("bid_price"))
                    .askPrice(rs.getDouble("ask_price"))
                    .spread(rs.getDouble("spread"))
                    .tradeTime(tradeTime)
                    .quoteTime(quoteTime)
                    .latencyMicros(latencyMicros)
                    .build();
        });
    }
}
