package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.model.Trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Deterministic trades for tests: 5 tickers, every 4th venue null, repetitive enough to compress well. */
public final class TestTrades {

    public static final List<String> TICKERS = List.of("AAPL", "AMZN", "GOOG", "MSFT", "NVDA");

    private TestTrades() {
    }

    public static List<Trade> generate(int count) {
        List<Trade> trades = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            trades.add(Trade.builder()
                    .tradeId("X-%07d".formatted(i))
                    .ticker(TICKERS.get(i % TICKERS.size()))
                    .price(BigDecimal.valueOf(100 + i % 50, 0).setScale(2))
                    .quantity(i % 100 + 1)
                    .tradeDate(LocalDate.of(2026, 1, 1).plusDays(i % 30))
                    .venue(i % 4 == 0 ? null : (i % 2 == 0 ? "NYSE" : "NASDAQ"))
                    .build());
        }
        return trades;
    }
}
