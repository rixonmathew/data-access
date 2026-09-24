package com.rixon.learn.spring.data.ducklake.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of {@code ducklake_merge_adjacent_files} for one table. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompactionResult {
    private long filesProcessed;
    private long filesCreated;
}
