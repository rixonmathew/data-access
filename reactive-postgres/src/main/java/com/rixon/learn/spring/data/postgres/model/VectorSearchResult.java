package com.rixon.learn.spring.data.postgres.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorSearchResult {
    private String id;
    private String ticker;
    private String title;
    private String summary;
    private String sector;
    private String sentiment;
    private double confidenceScore;
    private double similarity; // 1 - cosine_distance
    private double distance;   // raw distance (<=> or <->)
}
