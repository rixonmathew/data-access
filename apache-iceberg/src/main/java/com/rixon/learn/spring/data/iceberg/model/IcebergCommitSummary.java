package com.rixon.learn.spring.data.iceberg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IcebergCommitSummary {
    private long snapshotId;
    private Long parentSnapshotId;
    private String operation;
    private int manifestCount;
    private int dataFilesCount;
    private int schemaId;
    private int currentSpecId;
    private long timestampMillis;
    private String manifestListLocation;
}
