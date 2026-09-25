package com.rixon.learn.spring.data.databricks.model;

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
public class TableInfo {
    @JsonProperty("name")
    private String name;

    @JsonProperty("catalog_name")
    private String catalogName;

    @JsonProperty("schema_name")
    private String schemaName;

    @JsonProperty("table_type")
    private String tableType;

    @JsonProperty("data_source_format")
    private String dataSourceFormat;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("columns")
    private List<ColumnInfo> columns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type_text")
        private String typeText;

        @JsonProperty("nullable")
        private Boolean nullable;

        @JsonProperty("comment")
        private String comment;
    }
}
