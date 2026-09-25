package com.rixon.learn.spring.data.databricks.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databricks")
public class DatabricksController {

    private final DatabricksWorkspaceService workspaceService;
    private final DatabricksClusterManager clusterManager;
    private final DatabricksSqlService sqlService;

    public DatabricksController(DatabricksWorkspaceService workspaceService,
                                DatabricksClusterManager clusterManager,
                                DatabricksSqlService sqlService) {
        this.workspaceService = workspaceService;
        this.clusterManager = clusterManager;
        this.sqlService = sqlService;
    }

    @GetMapping("/user")
    public ResponseEntity<WorkspaceUserInfo> getCurrentUser() {
        return ResponseEntity.ok(workspaceService.getCurrentUser());
    }

    @GetMapping("/catalogs")
    public ResponseEntity<List<CatalogInfo>> listCatalogs() {
        return ResponseEntity.ok(workspaceService.listCatalogs());
    }

    @GetMapping("/schemas")
    public ResponseEntity<List<SchemaInfo>> listSchemas(@RequestParam(defaultValue = "samples") String catalog) {
        return ResponseEntity.ok(workspaceService.listSchemas(catalog));
    }

    @GetMapping("/tables")
    public ResponseEntity<List<TableInfo>> listTables(
            @RequestParam(defaultValue = "samples") String catalog,
            @RequestParam(defaultValue = "tpch") String schema) {
        return ResponseEntity.ok(workspaceService.listTables(catalog, schema));
    }

    @GetMapping("/clusters")
    public ResponseEntity<List<ClusterInfo>> listClusters() {
        return ResponseEntity.ok(clusterManager.listClusters());
    }

    @GetMapping("/audit")
    public ResponseEntity<CostAuditReport> auditCosts() {
        return ResponseEntity.ok(clusterManager.auditWorkspaceCosts());
    }

    @PostMapping("/emergency-shutdown")
    public ResponseEntity<Map<String, Object>> emergencyShutdown() {
        List<String> terminated = clusterManager.terminateAllRunningClusters();
        return ResponseEntity.ok(Map.of(
                "action", "EMERGENCY_SHUTDOWN",
                "terminatedClustersCount", terminated.size(),
                "terminatedClusterIds", terminated,
                "status", "ALL_ACTIVE_COMPUTE_TERMINATED"
        ));
    }

    @PostMapping("/sql")
    public ResponseEntity<SqlExecutionResult> executeSql(@RequestBody SqlExecutionRequest request) {
        return ResponseEntity.ok(sqlService.executeGuardedStatement(request));
    }
}
