package com.rixon.learn.spring.data.ducklake.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code ducklake_list_files}: a live Parquet data file and its optional delete file. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuckLakeDataFile {
    private String dataFile;
    private long dataFileSizeBytes;
    private String deleteFile;
}
