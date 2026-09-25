package com.rixon.learn.spring.data.timescaledb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimescaleTick {
    private Instant time;
    private String symbol;
    private double price;
    private long volume;
}
