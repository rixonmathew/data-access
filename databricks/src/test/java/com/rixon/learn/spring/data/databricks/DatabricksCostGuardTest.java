package com.rixon.learn.spring.data.databricks;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.guard.CostGuardViolationException;
import com.rixon.learn.spring.data.databricks.guard.DatabricksCostGuard;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterSafetyReport;
import com.rixon.learn.spring.data.databricks.model.ClusterSpec;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabricksCostGuardTest {

    private DatabricksProperties properties;
    private DatabricksCostGuard costGuard;

    @BeforeEach
    void setUp() {
        properties = new DatabricksProperties();
        properties.setFreeTierOnly(true);
        properties.setMaxAutoTerminationMinutes(10);
        properties.setMaxStatementTimeoutSeconds(30);
        properties.setMaxQueryRows(100);
        properties.setEnforceSingleNode(true);
        costGuard = new DatabricksCostGuard(properties);
    }

    @Test
    @DisplayName("Compliant Single-Node cluster spec passes cost guardrails")
    void testCompliantClusterSpecPasses() {
        ClusterSpec spec = ClusterSpec.createDefaultSafeSingleNode("safe-test-cluster");

        assertThatCode(() -> costGuard.validateClusterSpec(spec))
                .doesNotThrowAnyException();

        assertThat(spec.getSparkConf()).containsEntry("spark.databricks.cluster.profile", "singleNode");
        assertThat(spec.getSparkConf()).containsEntry("spark.master", "local[*]");
    }

    @Test
    @DisplayName("Multi-node cluster spec is strictly rejected to prevent compute multiplication")
    void testMultiNodeClusterRejected() {
        ClusterSpec spec = ClusterSpec.builder()
                .clusterName("expensive-multi-node")
                .numWorkers(3)
                .singleNode(false)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        assertThatThrownBy(() -> costGuard.validateClusterSpec(spec))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("FORBIDDEN_MULTI_NODE");
    }

    @Test
    @DisplayName("Cluster without auto-termination is rejected (prevents 24/7 billing)")
    void testZeroAutoterminationRejected() {
        ClusterSpec spec = ClusterSpec.builder()
                .clusterName("infinite-running-cluster")
                .numWorkers(0)
                .singleNode(true)
                .autoterminationMinutes(0)
                .nodeTypeId("i3.xlarge")
                .build();

        assertThatThrownBy(() -> costGuard.validateClusterSpec(spec))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("MISSING_AUTO_TERMINATION");
    }

    @Test
    @DisplayName("Excessive auto-termination (> 10 mins) is rejected")
    void testExcessiveAutoterminationRejected() {
        ClusterSpec spec = ClusterSpec.builder()
                .clusterName("long-idle-cluster")
                .numWorkers(0)
                .singleNode(true)
                .autoterminationMinutes(30)
                .nodeTypeId("i3.xlarge")
                .build();

        assertThatThrownBy(() -> costGuard.validateClusterSpec(spec))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("EXCESSIVE_AUTO_TERMINATION");
    }

    @Test
    @DisplayName("Disallowed / expensive GPU or high-memory node types are rejected")
    void testExpensiveNodeTypesRejected() {
        ClusterSpec spec = ClusterSpec.builder()
                .clusterName("gpu-deep-learning")
                .numWorkers(0)
                .singleNode(true)
                .autoterminationMinutes(10)
                .nodeTypeId("g4dn.12xlarge")
                .build();

        assertThatThrownBy(() -> costGuard.validateClusterSpec(spec))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("DISALLOWED_NODE_TYPE");
    }

    @Test
    @DisplayName("SQL statements exceeding execution timeout are rejected")
    void testSqlExcessiveTimeoutRejected() {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("SELECT * FROM samples.tpch.lineitem")
                .waitTimeoutSeconds(120)
                .maxRows(50)
                .build();

        assertThatThrownBy(() -> costGuard.validateSqlExecution(request))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("EXCESSIVE_SQL_TIMEOUT");
    }

    @Test
    @DisplayName("SQL statements exceeding row limit are rejected")
    void testSqlExcessiveRowsRejected() {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("SELECT * FROM samples.tpch.lineitem")
                .waitTimeoutSeconds(20)
                .maxRows(10000)
                .build();

        assertThatThrownBy(() -> costGuard.validateSqlExecution(request))
                .isInstanceOf(CostGuardViolationException.class)
                .hasMessageContaining("EXCESSIVE_QUERY_ROWS");
    }

    @Test
    @DisplayName("Empty SQL statements throw IllegalArgumentException")
    void testEmptySqlStatement() {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("   ")
                .build();

        assertThatThrownBy(() -> costGuard.validateSqlExecution(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Audit detects active running cluster as non-compliant risk")
    void testAuditDetectsActiveCluster() {
        ClusterInfo activeCluster = ClusterInfo.builder()
                .clusterId("0925-active-1")
                .clusterName("dev-cluster")
                .state("RUNNING")
                .numWorkers(0)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        ClusterSafetyReport report = costGuard.auditCluster(activeCluster);

        assertThat(report.isRunning()).isTrue();
        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolations()).anyMatch(v -> v.contains("currently ACTIVE"));
    }

    @Test
    @DisplayName("Audit detects terminated compliant cluster as 100% safe")
    void testAuditDetectsCompliantTerminatedCluster() {
        ClusterInfo terminatedCluster = ClusterInfo.builder()
                .clusterId("0925-term-1")
                .clusterName("test-cluster")
                .state("TERMINATED")
                .numWorkers(0)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        ClusterSafetyReport report = costGuard.auditCluster(terminatedCluster);

        assertThat(report.isRunning()).isFalse();
        assertThat(report.isCompliant()).isTrue();
        assertThat(report.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("Workspace audit accurately summarizes active compute risk")
    void testAuditWorkspace() {
        ClusterInfo activeCluster = ClusterInfo.builder()
                .clusterId("c1")
                .clusterName("active-1")
                .state("RUNNING")
                .numWorkers(0)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        ClusterInfo safeCluster = ClusterInfo.builder()
                .clusterId("c2")
                .clusterName("safe-1")
                .state("TERMINATED")
                .numWorkers(0)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        CostAuditReport audit = costGuard.auditWorkspace(List.of(activeCluster, safeCluster));

        assertThat(audit.getTotalClusters()).isEqualTo(2);
        assertThat(audit.getActiveClustersCount()).isEqualTo(1);
        assertThat(audit.getActiveClusterIds()).containsExactly("c1");
        assertThat(audit.isFullyCompliantWithFreeTier()).isFalse();
        assertThat(audit.getEstimatedHourlyCostUsd()).isGreaterThan(0.0);
    }
}
