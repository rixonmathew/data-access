package com.rixon.learn.spring.data.databricks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.databricks.controller.DatabricksController;
import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.CostAuditReport;
import com.rixon.learn.spring.data.databricks.model.SchemaInfo;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionRequest;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionResult;
import com.rixon.learn.spring.data.databricks.model.TableInfo;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import com.rixon.learn.spring.data.databricks.service.DatabricksClusterManager;
import com.rixon.learn.spring.data.databricks.service.DatabricksSqlService;
import com.rixon.learn.spring.data.databricks.service.DatabricksWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatabricksController.class)
class DatabricksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DatabricksWorkspaceService workspaceService;

    @MockitoBean
    private DatabricksClusterManager clusterManager;

    @MockitoBean
    private DatabricksSqlService sqlService;

    @Test
    @DisplayName("GET /api/databricks/user returns user profile")
    void testGetUser() throws Exception {
        WorkspaceUserInfo user = WorkspaceUserInfo.builder()
                .id("2102279257150258")
                .userName("rixon@databricks.com")
                .active(true)
                .build();
        when(workspaceService.getCurrentUser()).thenReturn(user);

        mockMvc.perform(get("/api/databricks/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("rixon@databricks.com"))
                .andExpect(jsonPath("$.id").value("2102279257150258"));
    }

    @Test
    @DisplayName("GET /api/databricks/catalogs returns Unity Catalog list")
    void testGetCatalogs() throws Exception {
        List<CatalogInfo> catalogs = List.of(
                CatalogInfo.builder().name("samples").catalogType("SYSTEM_CATALOG").build()
        );
        when(workspaceService.listCatalogs()).thenReturn(catalogs);

        mockMvc.perform(get("/api/databricks/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("samples"));
    }

    @Test
    @DisplayName("GET /api/databricks/schemas returns schemas")
    void testGetSchemas() throws Exception {
        List<SchemaInfo> schemas = List.of(
                SchemaInfo.builder().name("tpch").catalogName("samples").build()
        );
        when(workspaceService.listSchemas("samples")).thenReturn(schemas);

        mockMvc.perform(get("/api/databricks/schemas?catalog=samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("tpch"));
    }

    @Test
    @DisplayName("GET /api/databricks/tables returns tables")
    void testGetTables() throws Exception {
        List<TableInfo> tables = List.of(
                TableInfo.builder().name("customer").catalogName("samples").schemaName("tpch").build()
        );
        when(workspaceService.listTables("samples", "tpch")).thenReturn(tables);

        mockMvc.perform(get("/api/databricks/tables?catalog=samples&schema=tpch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("customer"));
    }

    @Test
    @DisplayName("GET /api/databricks/clusters returns clusters")
    void testGetClusters() throws Exception {
        List<ClusterInfo> clusters = List.of(
                ClusterInfo.builder().clusterId("c1").clusterName("test").state("TERMINATED").build()
        );
        when(clusterManager.listClusters()).thenReturn(clusters);

        mockMvc.perform(get("/api/databricks/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cluster_id").value("c1"));
    }

    @Test
    @DisplayName("GET /api/databricks/audit returns cost compliance report")
    void testGetAudit() throws Exception {
        CostAuditReport report = CostAuditReport.builder()
                .workspaceUrl("https://dbc-06bb552d-57db.cloud.databricks.com/?o=2102279257150258")
                .orgId("2102279257150258")
                .totalClusters(1)
                .activeClustersCount(0)
                .fullyCompliantWithFreeTier(true)
                .build();
        when(clusterManager.auditWorkspaceCosts()).thenReturn(report);

        mockMvc.perform(get("/api/databricks/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value("2102279257150258"))
                .andExpect(jsonPath("$.fullyCompliantWithFreeTier").value(true));
    }

    @Test
    @DisplayName("POST /api/databricks/emergency-shutdown terminates all active clusters")
    void testEmergencyShutdown() throws Exception {
        when(clusterManager.terminateAllRunningClusters()).thenReturn(List.of("c1", "c2"));

        mockMvc.perform(post("/api/databricks/emergency-shutdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("EMERGENCY_SHUTDOWN"))
                .andExpect(jsonPath("$.terminatedClustersCount").value(2))
                .andExpect(jsonPath("$.status").value("ALL_ACTIVE_COMPUTE_TERMINATED"));
    }

    @Test
    @DisplayName("POST /api/databricks/sql runs guarded query")
    void testExecuteSql() throws Exception {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("SELECT 1")
                .build();
        SqlExecutionResult result = SqlExecutionResult.builder()
                .statementId("stmt-1")
                .status("SUCCEEDED")
                .build();
        when(sqlService.executeGuardedStatement(any())).thenReturn(result);

        mockMvc.perform(post("/api/databricks/sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value("stmt-1"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
}
