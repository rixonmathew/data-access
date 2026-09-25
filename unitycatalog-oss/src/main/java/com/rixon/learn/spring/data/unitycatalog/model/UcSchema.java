package com.rixon.learn.spring.data.unitycatalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcSchema {
    @JsonProperty("schema_id")
    private String schemaId;
    private String name;
    @JsonProperty("catalog_name")
    private String catalogName;
    private String comment;
    @JsonProperty("created_at")
    private Long createdAt;
}
