package com.rixon.model.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String ticker,
        Instant timestamp,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal lastPrice,
        long volume
) {
    public MarketQuote(String ticker, BigDecimal bidPrice, BigDecimal askPrice, BigDecimal lastPrice, long volume) {
        this(ticker, Instant.now(), bidPrice, askPrice, lastPrice, volume);
    }
}
