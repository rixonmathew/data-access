package com.rixon.learn.spring.data.arrow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {
    private String tradeId;
    private String ticker;
    private BigDecimal price;
    private long quantity;
    private LocalDate tradeDate;
    private String venue; // Nullable
}
