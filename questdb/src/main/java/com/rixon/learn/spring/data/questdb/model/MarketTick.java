package com.rixon.learn.spring.data.questdb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTick {
    private String symbol;
    private double bid;
    private double ask;
    private double lastPrice;
    private long volume;
    private Instant timestamp;

    /**
     * Converts tick into InfluxDB Line Protocol (ILP) format for high-speed streaming ingestion.
     * Format: <table_name>,<symbols> <fields> <timestamp_nanos>
     */
    public String toIlp() {
        long nanos = timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
        return String.format("market_quotes,symbol=%s bid=%.4f,ask=%.4f,last_price=%.4f,volume=%di %d",
                symbol, bid, ask, lastPrice, volume, nanos);
    }
}
