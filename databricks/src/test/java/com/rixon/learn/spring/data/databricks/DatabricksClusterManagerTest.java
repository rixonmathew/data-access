package com.rixon.learn.spring.data.databricks;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.guard.CostGuardViolationException;
import com.rixon.learn.spring.data.databricks.guard.DatabricksCostGuard;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterSpec;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import com.rixon.learn.spring.data.databricks.service.DatabricksClusterManager;
import com.rixon.learn.spring.data.databricks.service.DatabricksRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabricksClusterManagerTest {

    @Mock
    private DatabricksRestClient restClient;

    private DatabricksProperties properties;
    private DatabricksCostGuard costGuard;
    private DatabricksClusterManager clusterManager;

    @BeforeEach
    void setUp() {
        properties = new DatabricksProperties();
        properties.setFreeTierOnly(true);
        costGuard = new DatabricksCostGuard(properties);
        clusterManager = new DatabricksClusterManager(restClient, costGuard, properties);
    }

    @Test
    @DisplayName("createSafeFreeTierCluster succeeds and creates Single-Node 10-min cluster")
    void testCreateSafeFreeTierCluster() {
        when(restClient.createCluster(any())).thenReturn("0925-safe-01");

        String clusterId = clusterManager.createSafeFreeTierCluster("unit-test-cluster");

        assertThat(clusterId).isEqualTo("0925-safe-01");
        verify(restClient).createCluster(any());
    }

    @Test
    @DisplayName("createSafeCluster rejects non-compliant specs before hitting the network")
    void testCreateSafeClusterRejectsMultiNode() {
        ClusterSpec unsafe = ClusterSpec.builder()
                .clusterName("unsafe-multi-node")
                .numWorkers(4)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        assertThatThrownBy(() -> clusterManager.createSafeCluster(unsafe))
                .isInstanceOf(CostGuardViolationException.class);

        verify(restClient, never()).createCluster(any());
    }

    @Test
    @DisplayName("terminateAllRunningClusters acts as an emergency killswitch, stopping only active clusters")
    void testEmergencyKillswitch() {
        ClusterInfo c1 = ClusterInfo.builder().clusterId("c1").state("RUNNING").build();
        ClusterInfo c2 = ClusterInfo.builder().clusterId("c2").state("TERMINATED").build();
        ClusterInfo c3 = ClusterInfo.builder().clusterId("c3").state("PENDING").build();

        when(restClient.listClusters()).thenReturn(List.of(c1, c2, c3));

        List<String> terminated = clusterManager.terminateAllRunningClusters();

        assertThat(terminated).containsExactlyInAnyOrder("c1", "c3");
        verify(restClient).terminateCluster("c1");
        verify(restClient).terminateCluster("c3");
        verify(restClient, never()).terminateCluster("c2");
    }

    @Test
    @DisplayName("withSafeCluster guarantees cluster termination even when consumer action throws")
    void testWithSafeClusterGuaranteesTerminationOnException() {
        when(restClient.createCluster(any())).thenReturn("temp-cluster-01");

        AtomicBoolean executed = new AtomicBoolean(false);

        assertThatThrownBy(() -> clusterManager.withSafeCluster("failing-task", cid -> {
            executed.set(true);
            throw new RuntimeException("Task failure simulation");
        })).isInstanceOf(RuntimeException.class).hasMessage("Task failure simulation");

        assertThat(executed.get()).isTrue();
        verify(restClient).terminateCluster("temp-cluster-01");
    }

    @Test
    @DisplayName("auditWorkspaceCosts reports cluster count and free-tier compliance")
    void testAuditWorkspaceCosts() {
        ClusterInfo safe = ClusterInfo.builder()
                .clusterId("safe-1")
                .clusterName("safe-cluster")
                .state("TERMINATED")
                .numWorkers(0)
                .autoterminationMinutes(10)
                .nodeTypeId("i3.xlarge")
                .build();

        when(restClient.listClusters()).thenReturn(List.of(safe));

        CostAuditReport report = clusterManager.auditWorkspaceCosts();

        assertThat(report.getTotalClusters()).isEqualTo(1);
        assertThat(report.getActiveClustersCount()).isEqualTo(0);
        assertThat(report.isFullyCompliantWithFreeTier()).isTrue();
    }
}
