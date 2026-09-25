package com.rixon.learn.spring.data.databricks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlExecutionRequest {
    private String statement;
    private String warehouseId;
    private String clusterId;
    @Builder.Default
    private String catalog = "samples";
    @Builder.Default
    private String schema = "tpch";
    @Builder.Default
    private int waitTimeoutSeconds = 30;
    @Builder.Default
    private int maxRows = 100;
}
