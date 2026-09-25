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
public class UcColumn {
    private String name;
    @JsonProperty("type_text")
    private String typeText;
    @JsonProperty("type_json")
    private String typeJson;
    @JsonProperty("type_name")
    private String typeName;
    @JsonProperty("type_precision")
    private Integer typePrecision;
    @JsonProperty("type_scale")
    private Integer typeScale;
    private Integer position;
    private Boolean nullable;
    private String comment;

    public static UcColumn of(String name, String typeText, String typeName, int position, boolean nullable) {
        String jsonType = typeText.toLowerCase();
        if ("int".equals(jsonType)) jsonType = "integer";
        String typeJson = String.format("{\"name\":\"%s\",\"type\":\"%s\",\"nullable\":%b,\"metadata\":{}}", name, jsonType, nullable);

        return UcColumn.builder()
                .name(name)
                .typeText(typeText)
                .typeJson(typeJson)
                .typeName(typeName)
                .typePrecision(0)
                .typeScale(0)
                .position(position)
                .nullable(nullable)
                .build();
    }
}
