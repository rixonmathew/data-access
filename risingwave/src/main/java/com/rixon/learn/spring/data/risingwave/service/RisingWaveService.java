package com.rixon.learn.spring.data.risingwave.service;

import com.rixon.learn.spring.data.risingwave.model.MarketTrade;
import com.rixon.learn.spring.data.risingwave.model.RealtimeVwap;
import com.rixon.learn.spring.data.risingwave.model.WashTradeAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

@Service
public class RisingWaveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RisingWaveService.class);

    private final JdbcTemplate jdbcTemplate;

    public RisingWaveService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        LOGGER.info("Initializing RisingWave streaming tables and materialized views...");
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            LOGGER.info("RisingWave schema initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RisingWave schema: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void truncateTrades() {
        jdbcTemplate.execute("DELETE FROM market_trades;");
        flush();
    }

    public void flush() {
        jdbcTemplate.execute("FLUSH;");
    }

    public void ingestTrades(List<MarketTrade> trades) {
        String sql = "INSERT INTO market_trades (trade_id, trader_id, symbol, price, quantity, side, trade_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, trades, trades.size(), (ps, trade) -> {
            ps.setString(1, trade.getTradeId());
            ps.setString(2, trade.getTraderId());
            ps.setString(3, trade.getSymbol());
            ps.setDouble(4, trade.getPrice());
            ps.setLong(5, trade.getQuantity());
            ps.setString(6, trade.getSide());
            ps.setTimestamp(7, Timestamp.from(trade.getTradeTime()));
        });
        flush();
        LOGGER.info("Ingested {} trades and flushed into RisingWave streaming pipeline.", trades.size());
    }

    public List<WashTradeAlert> getWashTradeAlerts() {
        String sql = "SELECT trader_id, symbol, buy_trade_id, sell_trade_id, buy_price, sell_price, volume, buy_time, sell_time " +
                     "FROM mv_wash_trading_alerts ORDER BY buy_time";
        return jdbcTemplate.query(sql, (rs, rowNum) -> WashTradeAlert.builder()
                .traderId(rs.getString("trader_id"))
                .symbol(rs.getString("symbol"))
                .buyTradeId(rs.getString("buy_trade_id"))
                .sellTradeId(rs.getString("sell_trade_id"))
                .buyPrice(rs.getDouble("buy_price"))
                .sellPrice(rs.getDouble("sell_price"))
                .volume(rs.getLong("volume"))
                .buyTime(rs.getTimestamp("buy_time").toInstant())
                .sellTime(rs.getTimestamp("sell_time").toInstant())
                .build());
    }

    public RealtimeVwap getRealtimeVwap(String symbol) {
        String sql = "SELECT symbol, trade_count, total_volume, vwap FROM mv_realtime_vwap WHERE symbol = ?";
        List<RealtimeVwap> results = jdbcTemplate.query(sql, (rs, rowNum) -> RealtimeVwap.builder()
                .symbol(rs.getString("symbol"))
                .tradeCount(rs.getLong("trade_count"))
                .totalVolume(rs.getLong("total_volume"))
                .vwap(rs.getDouble("vwap"))
                .build(), symbol);
        return results.isEmpty() ? null : results.get(0);
    }
}
