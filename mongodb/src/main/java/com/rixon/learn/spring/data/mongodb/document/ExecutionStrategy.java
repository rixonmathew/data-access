package com.rixon.learn.spring.data.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStrategy {
    private String strategyType; // e.g. TWAP, VWAP, ICEBERG, SNIPER
    private int sliceCount;
    private int durationMinutes;
    private double participationRate;
    private Map<String, Object> parameters;
}
