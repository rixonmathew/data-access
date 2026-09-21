package com.rixon.learn.spring.data.trino.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerAggregatedRisk {
    private String ticker;
    private long totalOrders;
    private BigDecimal totalQuantity;
    private BigDecimal totalNotionalUsd;
    private BigDecimal averagePrice;
}
