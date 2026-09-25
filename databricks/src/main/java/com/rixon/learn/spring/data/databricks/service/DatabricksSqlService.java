package com.rixon.learn.spring.data.databricks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.guard.DatabricksCostGuard;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionRequest;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes SQL statements on Databricks via the SQL Execution API or JDBC,
 * strictly governed by DatabricksCostGuard to prevent runaway compute.
 */
@Service
public class DatabricksSqlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksSqlService.class);
    private static final String DATABRICKS_JDBC_DRIVER = "com.databricks.client.jdbc.Driver";

    private final DatabricksRestClient restClient;
    private final DatabricksCostGuard costGuard;
    private final DatabricksProperties properties;

    public DatabricksSqlService(DatabricksRestClient restClient,
                                DatabricksCostGuard costGuard,
                                DatabricksProperties properties) {
        this.restClient = restClient;
        this.costGuard = costGuard;
        this.properties = properties;
    }

    /**
     * Executes a guarded SQL statement via the Databricks SQL Statements REST API.
     */
    public SqlExecutionResult executeGuardedStatement(SqlExecutionRequest request) {
        costGuard.validateSqlExecution(request);

        long start = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("statement", request.getStatement());
        if (request.getWarehouseId() != null && !request.getWarehouseId().isBlank()) {
            payload.put("warehouse_id", request.getWarehouseId());
        }
        if (request.getCatalog() != null && !request.getCatalog().isBlank()) {
            payload.put("catalog", request.getCatalog());
        }
        if (request.getSchema() != null && !request.getSchema().isBlank()) {
            payload.put("schema", request.getSchema());
        }
        payload.put("wait_timeout", request.getWaitTimeoutSeconds() + "s");
        payload.put("row_limit", request.getMaxRows());

        LOGGER.info("Executing guarded SQL statement (timeout: {}s, limit: {}): {}",
                request.getWaitTimeoutSeconds(), request.getMaxRows(), request.getStatement());

        JsonNode response = restClient.executeStatement(payload);
        long duration = System.currentTimeMillis() - start;

        return parseStatementResponse(response, duration);
    }

    /**
     * Executes a guarded query via the official Databricks JDBC driver.
     */
    public SqlExecutionResult executeJdbcQuery(String sql, int timeoutSeconds) {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement(sql)
                .waitTimeoutSeconds(timeoutSeconds)
                .maxRows(properties.getMaxQueryRows())
                .build();
        costGuard.validateSqlExecution(request);

        String jdbcUrl = buildJdbcUrl();
        LOGGER.info("Connecting via Databricks JDBC driver to {}...", properties.resolveHost());

        long start = System.currentTimeMillis();
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        try {
            Class.forName(DATABRICKS_JDBC_DRIVER);
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement()) {

                stmt.setQueryTimeout(timeoutSeconds);
                stmt.setMaxRows(request.getMaxRows());

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int count = md.getColumnCount();
                    for (int i = 1; i <= count; i++) {
                        columnNames.add(md.getColumnName(i));
                        columnTypes.add(md.getColumnTypeName(i));
                    }

                    while (rs.next() && rows.size() < request.getMaxRows()) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= count; i++) {
                            row.add(rs.getString(i));
                        }
                        rows.add(row);
                    }
                }
            }

            long duration = System.currentTimeMillis() - start;
            return SqlExecutionResult.builder()
                    .status("SUCCEEDED")
                    .columnNames(columnNames)
                    .columnTypes(columnTypes)
                    .rows(rows)
                    .executionDurationMs(duration)
                    .totalRowCount(rows.size())
                    .build();

        } catch (Exception e) {
            LOGGER.error("JDBC execution failed: {}", e.getMessage());
            long duration = System.currentTimeMillis() - start;
            return SqlExecutionResult.builder()
                    .status("FAILED")
                    .executionDurationMs(duration)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private String buildJdbcUrl() {
        String host = properties.resolveHost().replace("https://", "").replace("http://", "");
        String httpPath = properties.getHttpPath();
        if (httpPath == null || httpPath.isBlank()) {
            httpPath = "/sql/protocolv1/o/" + properties.resolveOrgId() + "/" +
                    (properties.getClusterId() != null ? properties.getClusterId() : "default");
        }
        return String.format("jdbc:databricks://%s:443/default;transportMode=http;ssl=1;AuthMech=3;httpPath=%s;PWD=%s",
                host, httpPath, properties.getToken() != null ? properties.getToken() : "");
    }

    private SqlExecutionResult parseStatementResponse(JsonNode response, long duration) {
        String statementId = response.has("statement_id") ? response.get("statement_id").asText() : null;
        String status = "UNKNOWN";
        if (response.has("status") && response.get("status").has("state")) {
            status = response.get("status").get("state").asText();
        }

        List<String> colNames = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        long totalRows = 0;

        if (response.has("manifest") && response.get("manifest").has("schema")) {
            JsonNode schemaNode = response.get("manifest").get("schema");
            if (schemaNode.has("columns")) {
                for (JsonNode col : schemaNode.get("columns")) {
                    colNames.add(col.has("name") ? col.get("name").asText() : "");
                    colTypes.add(col.has("type_text") ? col.get("type_text").asText() : "");
                }
            }
            if (response.get("manifest").has("total_row_count")) {
                totalRows = response.get("manifest").get("total_row_count").asLong();
            }
        }

        List<List<String>> rows = new ArrayList<>();
        if (response.has("result") && response.get("result").has("data_array")) {
            JsonNode dataArray = response.get("result").get("data_array");
            for (JsonNode rowNode : dataArray) {
                List<String> row = new ArrayList<>();
                for (JsonNode val : rowNode) {
                    row.add(val.isNull() ? null : val.asText());
                }
                rows.add(row);
            }
        }

        String errorMessage = null;
        if (response.has("status") && response.get("status").has("error")) {
            errorMessage = response.get("status").get("error").has("message")
                    ? response.get("status").get("error").get("message").asText()
                    : response.get("status").get("error").toString();
        }

        return SqlExecutionResult.builder()
                .statementId(statementId)
                .status(status)
                .columnNames(colNames)
                .columnTypes(colTypes)
                .rows(rows)
                .executionDurationMs(duration)
                .totalRowCount(totalRows > 0 ? totalRows : rows.size())
                .errorMessage(errorMessage)
                .build();
    }
}
