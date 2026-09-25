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
public class TradeQuoteMatch {
    private String tradeId;
    private String symbol;
    private double tradePrice;
    private long tradeQuantity;
    private double bidPrice;
    private double askPrice;
    private double spread;
    private Instant tradeTime;
    private Instant quoteTime;
    private long latencyMicros;
}
