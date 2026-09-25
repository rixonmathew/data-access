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
public class TradingAccount {
    private String accountId;
    private String accountName;
    private String currency;
    private BigDecimal balance;
    private Instant createdAt;
}
