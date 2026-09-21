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
public class FederatedOrderExposure {
    private String orderId;
    private String accountNumber;
    private String accountName;
    private String tier;
    private String ticker;
    private String side;
    private BigDecimal price;
    private BigDecimal quantity;
    private String currency;
    private BigDecimal fxRateToUsd;
    private BigDecimal notionalUsd;
}
