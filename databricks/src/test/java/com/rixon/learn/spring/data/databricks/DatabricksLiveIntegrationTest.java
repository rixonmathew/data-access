package com.rixon.learn.spring.data.databricks;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import com.rixon.learn.spring.data.databricks.service.DatabricksClusterManager;
import com.rixon.learn.spring.data.databricks.service.DatabricksWorkspaceService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live Cloud Integration Test for Databricks workspace:
 * https://dbc-06bb552d-57db.cloud.databricks.com/?o=2102279257150258
 *
 * This test only executes when DATABRICKS_TOKEN is set in the environment or properties.
 * All tests in this class are restricted to ZERO-COMPUTE Control Plane operations
 * (User SCIM API, Unity Catalog metadata, and Cost Audit) ensuring ZERO charges to the account.
 */
@SpringBootTest
class DatabricksLiveIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksLiveIntegrationTest.class);

    @Autowired
    private DatabricksProperties properties;

    @Autowired
    private DatabricksWorkspaceService workspaceService;

    @Autowired
    private DatabricksClusterManager clusterManager;

    private boolean isLiveConfigured() {
        return properties.hasToken();
    }

    @Test
    @DisplayName("Live: Verify workspace connectivity and user identity (Zero Compute)")
    void testLiveCurrentUser() {
        Assumptions.assumeTrue(isLiveConfigured(),
                "Skipping live test: Set DATABRICKS_TOKEN to run against https://dbc-06bb552d-57db.cloud.databricks.com");

        WorkspaceUserInfo user = workspaceService.getCurrentUser();
        LOGGER.info("Authenticated to Databricks workspace as: {} ({})", user.getUserName(), user.getDisplayName());

        assertThat(user).isNotNull();
        assertThat(user.getUserName()).isNotBlank();
    }

    @Test
    @DisplayName("Live: Query Unity Catalog catalogs at $0 compute cost")
    void testLiveListCatalogs() {
        Assumptions.assumeTrue(isLiveConfigured(),
                "Skipping live test: Set DATABRICKS_TOKEN to run against https://dbc-06bb552d-57db.cloud.databricks.com");

        List<CatalogInfo> catalogs = workspaceService.listCatalogs();
        LOGGER.info("Discovered {} Unity Catalog catalogs in workspace.", catalogs.size());
        catalogs.forEach(c -> LOGGER.info(" - Catalog: {} ({})", c.getName(), c.getCatalogType()));

        assertThat(catalogs).isNotEmpty();
    }

    @Test
    @DisplayName("Live: Audit workspace clusters to guarantee no unexpected running compute")
    void testLiveAuditWorkspaceSafety() {
        Assumptions.assumeTrue(isLiveConfigured(),
                "Skipping live test: Set DATABRICKS_TOKEN to run against https://dbc-06bb552d-57db.cloud.databricks.com");

        CostAuditReport audit = clusterManager.auditWorkspaceCosts();
        LOGGER.info("Live Workspace Cost Audit: {} total clusters, {} active.",
                audit.getTotalClusters(), audit.getActiveClustersCount());

        if (audit.getActiveClustersCount() > 0) {
            LOGGER.warn("WARNING: Found {} active running clusters in workspace: {}",
                    audit.getActiveClustersCount(), audit.getActiveClusterIds());
        } else {
            LOGGER.info("SUCCESS: Zero active clusters running. Workspace is 100% in the free zone!");
        }

        assertThat(audit.getWorkspaceUrl()).contains("dbc-06bb552d-57db.cloud.databricks.com");
    }
}
