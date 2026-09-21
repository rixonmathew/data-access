package com.rixon.learn.spring.data.deltalake.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeltaCommitSummary {
    private long version;
    private String operation;
    private int activeFilesCount;
    private long timestamp;
    private String engineInfo;
}
