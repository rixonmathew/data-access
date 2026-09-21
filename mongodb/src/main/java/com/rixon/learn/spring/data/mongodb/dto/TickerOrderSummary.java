package com.rixon.learn.spring.data.mongodb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerOrderSummary {
    private String ticker;
    private long totalOrders;
    private BigDecimal totalVolume;
    private BigDecimal averagePrice;
}
