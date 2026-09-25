package com.rixon.learn.spring.data.databricks.guard;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterSafetyReport;
import com.rixon.learn.spring.data.databricks.model.ClusterSpec;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DatabricksCostGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksCostGuard.class);

    private final DatabricksProperties properties;

    public DatabricksCostGuard(DatabricksProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates a cluster specification against Free Tier safety policies before
     * sending the creation request to Databricks.
     */
    public void validateClusterSpec(ClusterSpec spec) {
        if (!properties.isFreeTierOnly()) {
            return;
        }

        // Rule 1: Single Node Only
        if (properties.isEnforceSingleNode() && (spec.getNumWorkers() > 0 || !spec.isSingleNode())) {
            throw new CostGuardViolationException(
                    "FORBIDDEN_MULTI_NODE",
                    "Multi-node clusters (" + spec.getNumWorkers() + " workers) violate Free Zone policies! " +
                    "Free-tier safety requires num_workers = 0 and singleNode = true to prevent multi-instance cloud charges."
            );
        }

        // Rule 2: Mandatory Auto-Termination
        if (spec.getAutoterminationMinutes() <= 0) {
            throw new CostGuardViolationException(
                    "MISSING_AUTO_TERMINATION",
                    "Auto-termination cannot be disabled (was: " + spec.getAutoterminationMinutes() + "). " +
                    "Clusters left running indefinitely are the #1 cause of unexpected cloud and DBU bills."
            );
        }

        // Rule 3: Auto-Termination Threshold
        if (spec.getAutoterminationMinutes() > properties.getMaxAutoTerminationMinutes()) {
            throw new CostGuardViolationException(
                    "EXCESSIVE_AUTO_TERMINATION",
                    "Auto-termination of " + spec.getAutoterminationMinutes() + " minutes exceeds the maximum allowed " +
                    "free zone threshold of " + properties.getMaxAutoTerminationMinutes() + " minutes."
            );
        }

        // Rule 4: Node Type Allowlist
        if (spec.getNodeTypeId() != null && !isNodeTypeAllowed(spec.getNodeTypeId())) {
            throw new CostGuardViolationException(
                    "DISALLOWED_NODE_TYPE",
                    "Node type '" + spec.getNodeTypeId() + "' is not approved for free tier testing. " +
                    "Permitted minimal-cost instance types: " + properties.getAllowedNodeTypes()
            );
        }

        // Ensure singleNode configuration flags exist
        if (spec.getSparkConf() != null) {
            spec.getSparkConf().putIfAbsent("spark.databricks.cluster.profile", "singleNode");
            spec.getSparkConf().putIfAbsent("spark.master", "local[*]");
        }
        if (spec.getCustomTags() != null) {
            spec.getCustomTags().putIfAbsent("ResourceClass", "SingleNode");
            spec.getCustomTags().putIfAbsent("Tier", "FreeZoneTesting");
        }

        LOGGER.info("Cluster spec '{}' passed all Databricks Free Zone cost guardrails.", spec.getClusterName());
    }

    /**
     * Validates a SQL statement execution request to ensure query runtime and bounds
     * are strictly controlled within free tier parameters.
     */
    public void validateSqlExecution(SqlExecutionRequest request) {
        if (!properties.isFreeTierOnly()) {
            return;
        }

        if (request.getStatement() == null || request.getStatement().isBlank()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty.");
        }

        if (request.getWaitTimeoutSeconds() > properties.getMaxStatementTimeoutSeconds()) {
            throw new CostGuardViolationException(
                    "EXCESSIVE_SQL_TIMEOUT",
                    "Requested SQL timeout of " + request.getWaitTimeoutSeconds() + "s exceeds the free zone limit of " +
                    properties.getMaxStatementTimeoutSeconds() + "s."
            );
        }

        if (request.getMaxRows() > properties.getMaxQueryRows()) {
            throw new CostGuardViolationException(
                    "EXCESSIVE_QUERY_ROWS",
                    "Requested row limit of " + request.getMaxRows() + " exceeds the free zone maximum of " +
                    properties.getMaxQueryRows() + " rows."
            );
        }

        String sqlUpper = request.getStatement().trim().toUpperCase(Locale.ROOT);
        if (sqlUpper.startsWith("SELECT") && !sqlUpper.contains("LIMIT")) {
            LOGGER.warn("SQL statement lacks an explicit LIMIT clause. Free Zone guard recommends adding LIMIT {}.",
                    request.getMaxRows());
        }
    }

    /**
     * Audits an existing cluster for cost compliance.
     */
    public ClusterSafetyReport auditCluster(ClusterInfo cluster) {
        List<String> violations = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        boolean running = cluster.isRunning();
        if (running) {
            violations.add("Cluster is currently ACTIVE (" + cluster.getState() + ") and incurring compute / DBU charges.");
            recommendations.add("Terminate cluster immediately if not actively in use.");
        }

        if (cluster.getNumWorkers() != null && cluster.getNumWorkers() > 0) {
            violations.add("Cluster has " + cluster.getNumWorkers() + " worker nodes (multi-node architecture).");
            recommendations.add("Reconfigure cluster to Single-Node mode (0 workers).");
        }

        if (cluster.getAutoterminationMinutes() == null || cluster.getAutoterminationMinutes() <= 0) {
            violations.add("CRITICAL: Auto-termination is disabled! Cluster will run perpetually if started.");
            recommendations.add("Set auto-termination to <= " + properties.getMaxAutoTerminationMinutes() + " minutes.");
        } else if (cluster.getAutoterminationMinutes() > properties.getMaxAutoTerminationMinutes()) {
            violations.add("Auto-termination is set to " + cluster.getAutoterminationMinutes() +
                    " mins (threshold: " + properties.getMaxAutoTerminationMinutes() + " mins).");
            recommendations.add("Lower auto-termination to " + properties.getMaxAutoTerminationMinutes() + " minutes.");
        }

        if (cluster.getNodeTypeId() != null && !isNodeTypeAllowed(cluster.getNodeTypeId())) {
            violations.add("Cluster uses node type '" + cluster.getNodeTypeId() + "' which is outside the approved free tier list.");
            recommendations.add("Switch node type to one of: " + properties.getAllowedNodeTypes());
        }

        boolean compliant = violations.isEmpty();

        return ClusterSafetyReport.builder()
                .clusterId(cluster.getClusterId())
                .clusterName(cluster.getClusterName())
                .state(cluster.getState())
                .isRunning(running)
                .isCompliant(compliant)
                .violations(violations)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Audits all clusters in the workspace and produces a holistic cost audit report.
     */
    public CostAuditReport auditWorkspace(List<ClusterInfo> clusters) {
        List<String> activeIds = new ArrayList<>();
        List<ClusterSafetyReport> reports = new ArrayList<>();
        List<String> criticalAlerts = new ArrayList<>();
        List<String> optimizationTips = new ArrayList<>();

        double activeHourlyCost = 0.0;

        for (ClusterInfo cluster : clusters) {
            ClusterSafetyReport report = auditCluster(cluster);
            reports.add(report);

            if (report.isRunning()) {
                activeIds.add(cluster.getClusterId());
                criticalAlerts.add("Active cluster found: '" + cluster.getClusterName() +
                        "' (" + cluster.getClusterId() + ") in state " + cluster.getState());
                // Typical AWS EC2 + DBU hourly estimate for small single node cluster ~ $0.35 - $0.75/hr
                activeHourlyCost += 0.50;
            }

            if (!report.isCompliant()) {
                for (String violation : report.getViolations()) {
                    if (violation.startsWith("CRITICAL:")) {
                        criticalAlerts.add(cluster.getClusterName() + ": " + violation);
                    }
                }
            }
        }

        if (activeIds.isEmpty()) {
            optimizationTips.add("No clusters currently running. Workspace is 100% in the zero-compute Free Zone!");
        } else {
            optimizationTips.add("Use DatabricksClusterManager.terminateAllRunningClusters() or emergency-shutdown endpoint to stop active compute.");
        }

        optimizationTips.add("Prefer Unity Catalog metadata APIs (catalogs, schemas, tables) for testing; they cost $0 and require 0 compute.");
        optimizationTips.add("Always use Single-Node clusters (0 workers) with max 10 min autotermination for code execution.");

        boolean allCompliant = criticalAlerts.isEmpty() && activeIds.isEmpty();

        return CostAuditReport.builder()
                .workspaceUrl(properties.getWorkspaceUrl())
                .orgId(properties.resolveOrgId())
                .totalClusters(clusters.size())
                .activeClustersCount(activeIds.size())
                .activeClusterIds(activeIds)
                .clusterReports(reports)
                .fullyCompliantWithFreeTier(allCompliant)
                .estimatedHourlyCostUsd(activeHourlyCost)
                .criticalCostAlerts(criticalAlerts)
                .optimizationTips(optimizationTips)
                .build();
    }

    private boolean isNodeTypeAllowed(String nodeTypeId) {
        if (properties.getAllowedNodeTypes().contains(nodeTypeId)) {
            return true;
        }
        for (String allowed : properties.getAllowedNodeTypes()) {
            if (nodeTypeId.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }
}
