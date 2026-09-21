package com.rixon.learn.spring.data.clickhouse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTickRow {
    private String ticker;
    private Timestamp eventTime;
    private double bid;
    private double ask;
    private double lastPrice;
    private long volume;
    private int latencyMicros;
}
