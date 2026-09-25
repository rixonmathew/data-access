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
public class CostAuditReport {
    private String workspaceUrl;
    private String orgId;
    private int totalClusters;
    private int activeClustersCount;
    @Builder.Default
    private List<String> activeClusterIds = new ArrayList<>();
    @Builder.Default
    private List<ClusterSafetyReport> clusterReports = new ArrayList<>();
    private boolean fullyCompliantWithFreeTier;
    private double estimatedHourlyCostUsd;
    @Builder.Default
    private List<String> criticalCostAlerts = new ArrayList<>();
    @Builder.Default
    private List<String> optimizationTips = new ArrayList<>();
}
