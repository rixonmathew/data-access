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
public class TradeRecord {
    private String tradeId;
    private String symbol;
    private double price;
    private long quantity;
    private String side;
    private Instant timestamp;

    public String toIlp() {
        long nanos = timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
        return String.format("trade_executions,symbol=%s,side=%s trade_id=\"%s\",price=%.4f,quantity=%di %d",
                symbol, side, tradeId, price, quantity, nanos);
    }
}
