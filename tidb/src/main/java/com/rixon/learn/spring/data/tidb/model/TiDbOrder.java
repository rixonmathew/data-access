package com.rixon.learn.spring.data.tidb.model;

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
public class TiDbOrder {
    private String orderId;
    private String accountId;
    private String symbol;
    private String side; // "BUY" or "SELL"
    private String orderType; // "LIMIT" or "MARKET"
    private BigDecimal price;
    private Long quantity;
    private String status; // "PENDING", "FILLED", "CANCELLED"
    private Instant createdAt;
}
