package com.rixon.learn.spring.data.timescaledb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimescaleCompressionStat {
    private String hypertableName;
    private long totalChunks;
    private long compressedChunks;
    private long uncompressedBytes;
    private long compressedBytes;
}
