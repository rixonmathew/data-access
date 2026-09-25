package com.rixon.learn.spring.data.risingwave.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeVwap {
    private String symbol;
    private Long tradeCount;
    private Long totalVolume;
    private Double vwap;
}
