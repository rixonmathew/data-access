package com.rixon.learn.spring.data.databricks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterSpec {
    private String clusterName;
    @Builder.Default
    private String sparkVersion = "14.3.x-scala2.12";
    @Builder.Default
    private String nodeTypeId = "i3.xlarge";
    @Builder.Default
    private int autoterminationMinutes = 10;
    @Builder.Default
    private int numWorkers = 0;
    @Builder.Default
    private boolean singleNode = true;
    @Builder.Default
    private Map<String, String> sparkConf = new HashMap<>();
    @Builder.Default
    private Map<String, String> customTags = new HashMap<>();

    public static ClusterSpec createDefaultSafeSingleNode(String clusterName) {
        Map<String, String> conf = new HashMap<>();
        conf.put("spark.databricks.cluster.profile", "singleNode");
        conf.put("spark.master", "local[*]");

        Map<String, String> tags = new HashMap<>();
        tags.put("ResourceClass", "SingleNode");
        tags.put("Tier", "FreeZoneTesting");

        return ClusterSpec.builder()
                .clusterName(clusterName)
                .sparkVersion("14.3.x-scala2.12")
                .nodeTypeId("i3.xlarge")
                .autoterminationMinutes(10)
                .numWorkers(0)
                .singleNode(true)
                .sparkConf(conf)
                .customTags(tags)
                .build();
    }
}
