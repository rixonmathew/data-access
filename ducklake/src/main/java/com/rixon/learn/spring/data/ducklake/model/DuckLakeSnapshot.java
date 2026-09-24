package com.rixon.learn.spring.data.ducklake.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** One row of {@code <catalog>.snapshots()}: every committed DuckLake transaction creates one. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuckLakeSnapshot {
    private long snapshotId;
    private OffsetDateTime snapshotTime;
    private long schemaVersion;
    /** Rendered change map, e.g. {@code {tables_inserted_into=[1]}}. */
    private String changes;
}
