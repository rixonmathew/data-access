package com.rixon.learn.spring.data.databricks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterSafetyReport {
    private String clusterId;
    private String clusterName;
    private String state;
    private boolean isRunning;
    private boolean isCompliant;
    @Builder.Default
    private List<String> violations = new ArrayList<>();
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
}
