package com.rixon.learn.spring.data.risingwave.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTrade {
    private String tradeId;
    private String traderId;
    private String symbol;
    private Double price;
    private Long quantity;
    private String side; // "BUY" or "SELL"
    private Instant tradeTime;
}
