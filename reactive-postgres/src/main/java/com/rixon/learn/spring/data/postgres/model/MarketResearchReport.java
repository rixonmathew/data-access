package com.rixon.learn.spring.data.postgres.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketResearchReport {
    private String id;
    private String ticker;
    private String title;
    private String summary;
    private String sector;
    private String sentiment; // BULLISH, BEARISH, NEUTRAL
    private double confidenceScore;
    private float[] embedding;
    private Instant createdAt;
}
