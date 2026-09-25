package com.rixon.learn.spring.data.spanner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHierarchySummary {
    private String accountId;
    private String accountName;
    private BigDecimal balance;
    private Long orderCount;
    private Long executionCount;
    private Long totalExecutedQuantity;
    private BigDecimal totalExecutedNotional;
}
