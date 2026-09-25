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
public class CandleStick {
    private Instant timestamp;
    private String symbol;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double vwap;
}
