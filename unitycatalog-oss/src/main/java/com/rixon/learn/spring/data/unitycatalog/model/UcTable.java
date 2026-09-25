package com.rixon.learn.spring.data.unitycatalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcTable {
    @JsonProperty("table_id")
    private String tableId;
    private String name;
    @JsonProperty("catalog_name")
    private String catalogName;
    @JsonProperty("schema_name")
    private String schemaName;
    @JsonProperty("table_type")
    private String tableType; // "MANAGED", "EXTERNAL"
    @JsonProperty("data_source_format")
    private String dataSourceFormat; // "DELTA", "ICEBERG", "PARQUET"
    private List<UcColumn> columns;
    @JsonProperty("storage_location")
    private String storageLocation;
    private String comment;
    @JsonProperty("created_at")
    private Long createdAt;
}
