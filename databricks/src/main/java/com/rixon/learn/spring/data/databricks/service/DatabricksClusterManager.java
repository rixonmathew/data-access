package com.rixon.learn.spring.data.databricks.service;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.guard.DatabricksCostGuard;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterSpec;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages Databricks compute lifecycle under strict Free-Tier cost guardrails.
 */
@Service
public class DatabricksClusterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksClusterManager.class);

    private final DatabricksRestClient restClient;
    private final DatabricksCostGuard costGuard;
    private final DatabricksProperties properties;

    public DatabricksClusterManager(DatabricksRestClient restClient,
                                    DatabricksCostGuard costGuard,
                                    DatabricksProperties properties) {
        this.restClient = restClient;
        this.costGuard = costGuard;
        this.properties = properties;
    }

    /**
     * Lists all clusters in the workspace.
     */
    public List<ClusterInfo> listClusters() {
        return restClient.listClusters();
    }

    /**
     * Retrieves status and details of a single cluster.
     */
    public ClusterInfo getCluster(String clusterId) {
        return restClient.getCluster(clusterId);
    }

    /**
     * Creates a minimal Single-Node cluster guaranteed to comply with Free Tier rules.
     * Enforces autotermination (10 mins) and 0 workers.
     */
    public String createSafeFreeTierCluster(String clusterName) {
        ClusterSpec spec = ClusterSpec.createDefaultSafeSingleNode(clusterName);
        return createSafeCluster(spec);
    }

    /**
     * Creates a cluster from the provided specification after passing it through
     * the DatabricksCostGuard safety enforcer.
     */
    public String createSafeCluster(ClusterSpec spec) {
        costGuard.validateClusterSpec(spec);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cluster_name", spec.getClusterName());
        payload.put("spark_version", spec.getSparkVersion());
        payload.put("node_type_id", spec.getNodeTypeId());
        payload.put("num_workers", spec.getNumWorkers());
        payload.put("autotermination_minutes", spec.getAutoterminationMinutes());
        payload.put("spark_conf", spec.getSparkConf());
        payload.put("custom_tags", spec.getCustomTags());

        LOGGER.info("Sending safe cluster creation request for '{}' (autotermination: {} min, singleNode: true)...",
                spec.getClusterName(), spec.getAutoterminationMinutes());

        String clusterId = restClient.createCluster(payload);
        LOGGER.info("Cluster '{}' created successfully with ID: {}", spec.getClusterName(), clusterId);
        return clusterId;
    }

    /**
     * Immediately stops / terminates a cluster to prevent ongoing cloud compute charges.
     */
    public void terminateCluster(String clusterId) {
        LOGGER.info("Terminating cluster '{}' to return compute cost to $0...", clusterId);
        restClient.terminateCluster(clusterId);
    }

    /**
     * Emergency Killswitch: Scans all clusters in the workspace and immediately terminates
     * every active or pending cluster to stop all cloud charges.
     */
    public List<String> terminateAllRunningClusters() {
        LOGGER.warn("Triggering Databricks Emergency Killswitch: terminating all active clusters...");
        List<ClusterInfo> clusters = listClusters();
        List<String> terminatedIds = new ArrayList<>();

        for (ClusterInfo cluster : clusters) {
            if (cluster.isRunning()) {
                LOGGER.info("Emergency kill: Terminating running cluster '{}' ({})",
                        cluster.getClusterName(), cluster.getClusterId());
                try {
                    terminateCluster(cluster.getClusterId());
                    terminatedIds.add(cluster.getClusterId());
                } catch (Exception e) {
                    LOGGER.error("Failed to terminate cluster '{}': {}", cluster.getClusterId(), e.getMessage());
                }
            }
        }

        LOGGER.info("Emergency killswitch finished. Terminated {} active clusters.", terminatedIds.size());
        return terminatedIds;
    }

    /**
     * Generates a comprehensive cost and compliance audit of the workspace.
     */
    public CostAuditReport auditWorkspaceCosts() {
        List<ClusterInfo> clusters = listClusters();
        return costGuard.auditWorkspace(clusters);
    }

    /**
     * Lifecycle helper: Creates a safe cluster, runs an action, and GUARANTEES cluster
     * termination in a finally block so no compute is left running.
     */
    public void withSafeCluster(String clusterName, Consumer<String> clusterAction) {
        String clusterId = null;
        try {
            clusterId = createSafeFreeTierCluster(clusterName);
            clusterAction.accept(clusterId);
        } finally {
            if (clusterId != null) {
                try {
                    terminateCluster(clusterId);
                } catch (Exception e) {
                    LOGGER.warn("Failed to auto-terminate cluster '{}' in finally block: {}", clusterId, e.getMessage());
                }
            }
        }
    }
}
