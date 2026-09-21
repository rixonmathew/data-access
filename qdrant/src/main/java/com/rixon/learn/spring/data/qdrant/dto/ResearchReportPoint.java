package com.rixon.learn.spring.data.qdrant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResearchReportPoint {
    private long id;
    private String ticker;
    private String title;
    private String summary;
    private String sector;
    private String sentiment;
    private double confidenceScore;
    private List<Float> vector;
}
