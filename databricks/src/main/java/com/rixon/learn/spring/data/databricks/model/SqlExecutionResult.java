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
public class SqlExecutionResult {
    private String statementId;
    private String status;
    @Builder.Default
    private List<String> columnNames = new ArrayList<>();
    @Builder.Default
    private List<String> columnTypes = new ArrayList<>();
    @Builder.Default
    private List<List<String>> rows = new ArrayList<>();
    private long executionDurationMs;
    private long totalRowCount;
    private boolean truncated;
    private String errorMessage;
}
