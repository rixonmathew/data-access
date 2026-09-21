package com.rixon.learn.spring.data.clickhouse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickerMetricsDto {
    private String ticker;
    private long tickCount;
    private double avgPrice;
    private double minPrice;
    private double maxPrice;
    private long totalVolume;
    private double p50Latency;
    private double p95Latency;
    private double p99Latency;
}
