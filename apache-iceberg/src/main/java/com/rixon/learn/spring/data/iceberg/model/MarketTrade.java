package com.rixon.learn.spring.data.iceberg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTrade {
    private String tradeId;
    private String ticker;
    private double price;
    private long quantity;
    private String side;
    private long executedAt;
    private String venue; // Nullable, added during schema evolution
}
