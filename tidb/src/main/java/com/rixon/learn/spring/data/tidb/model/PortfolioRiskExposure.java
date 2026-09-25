package com.rixon.learn.spring.data.tidb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRiskExposure {
    private String accountId;
    private String symbol;
    private Long netPosition; // (Buy Quantity - Sell Quantity)
    private Long grossVolume; // (Buy Quantity + Sell Quantity)
    private BigDecimal grossNotional; // Sum(Price * Quantity)
    private BigDecimal vwapBuy;
    private BigDecimal vwapSell;
}
