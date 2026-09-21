package com.rixon.learn.spring.data.cdc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent {
    private String eventId;
    private String orderId;
    private String accountNumber;
    private String ticker;
    private String side;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant timestamp;
}
