package com.rixon.learn.spring.data.spanner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecution {
    private String accountId;
    private String orderId;
    private String executionId;
    private BigDecimal executionPrice;
    private Long executedQuantity;
    private Instant executedAt;
}
