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
public class UcVolume {
    @JsonProperty("volume_id")
    private String volumeId;
    private String name;
    @JsonProperty("catalog_name")
    private String catalogName;
    @JsonProperty("schema_name")
    private String schemaName;
    @JsonProperty("volume_type")
    private String volumeType; // "MANAGED", "EXTERNAL"
    @JsonProperty("storage_location")
    private String storageLocation;
    private String comment;
    @JsonProperty("created_at")
    private Long createdAt;
}
