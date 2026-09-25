package com.rixon.learn.spring.data.databricks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterInfo {
    @JsonProperty("cluster_id")
    private String clusterId;

    @JsonProperty("cluster_name")
    private String clusterName;

    @JsonProperty("spark_version")
    private String sparkVersion;

    @JsonProperty("node_type_id")
    private String nodeTypeId;

    @JsonProperty("driver_node_type_id")
    private String driverNodeTypeId;

    @JsonProperty("autotermination_minutes")
    private Integer autoterminationMinutes;

    @JsonProperty("num_workers")
    private Integer numWorkers;

    @JsonProperty("state")
    private String state;

    @JsonProperty("state_message")
    private String stateMessage;

    @JsonProperty("spark_conf")
    private Map<String, String> sparkConf;

    @JsonProperty("custom_tags")
    private Map<String, String> customTags;

    public boolean isRunning() {
        return "RUNNING".equalsIgnoreCase(state) ||
               "PENDING".equalsIgnoreCase(state) ||
               "RESIZING".equalsIgnoreCase(state) ||
               "RESTARTING".equalsIgnoreCase(state);
    }

    public boolean isSingleNode() {
        boolean zeroWorkers = numWorkers == null || numWorkers == 0;
        boolean singleNodeTag = customTags != null && "SingleNode".equalsIgnoreCase(customTags.get("ResourceClass"));
        boolean singleNodeConf = sparkConf != null && "singleNode".equalsIgnoreCase(sparkConf.get("spark.databricks.cluster.profile"));
        return zeroWorkers || singleNodeTag || singleNodeConf;
    }
}
