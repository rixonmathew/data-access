package com.rixon.learn.spring.data.postgres.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderWithResearchInsight {
    private String orderId;
    private String accountNumber;
    private String ticker;
    private String side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String reportTitle;
    private String sentiment;
    private double confidenceScore;
    private double vectorSimilarity;
}
