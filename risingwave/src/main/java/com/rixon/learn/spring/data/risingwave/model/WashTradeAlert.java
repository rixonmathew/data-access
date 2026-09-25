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
public class WashTradeAlert {
    private String traderId;
    private String symbol;
    private String buyTradeId;
    private String sellTradeId;
    private Double buyPrice;
    private Double sellPrice;
    private Long volume;
    private Instant buyTime;
    private Instant sellTime;
}
