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
public class UcCatalog {
    private String id;
    private String name;
    private String comment;
    @JsonProperty("created_at")
    private Long createdAt;
    @JsonProperty("updated_at")
    private Long updatedAt;
}
