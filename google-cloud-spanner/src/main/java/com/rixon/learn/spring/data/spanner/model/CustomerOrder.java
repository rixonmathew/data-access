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
public class CustomerOrder {
    private String accountId;
    private String orderId;
    private String symbol;
    private String side; // "BUY" or "SELL"
    private BigDecimal price;
    private Long quantity;
    private String status; // "PENDING", "FILLED", "CANCELLED"
    private Instant createdAt;
}
