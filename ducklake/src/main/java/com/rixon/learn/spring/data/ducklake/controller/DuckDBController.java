package com.rixon.learn.spring.data.ducklake.controller;

import com.rixon.learn.spring.data.ducklake.service.DuckDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Ad-hoc access to the embedded DuckDB engine. {@code /query} runs arbitrary SQL by design:
 * this is a local exploration endpoint, not something to expose publicly.
 */
@Slf4j
@RestController
@RequestMapping("/api/duckdb")
@RequiredArgsConstructor
public class DuckDBController {

    private final DuckDBService duckDBService;

    /**
     * Creates a table from a local file or an S3 object.
     *
     * @param tableName The name of the table to create
     * @param path Local path or {@code s3://bucket/key}
     * @param format CSV or PARQUET
     */
    @PostMapping("/tables")
    public ResponseEntity<?> createTable(
            @RequestParam String tableName,
            @RequestParam String path,
            @RequestParam String format) {

        try {
            duckDBService.createTableFromFile(tableName, path, format);
            return ResponseEntity.ok(Map.of(
                "message", "Table created successfully",
                "tableName", tableName,
                "path", path
            ));
        } catch (SQLException | IllegalArgumentException e) {
            log.error("Error creating table from {}", path, e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to create table",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Executes a SQL query or statement against DuckDB.
     *
     * @param query The SQL to execute
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody String query) {
        try {
            String upperQuery = query.trim().toUpperCase();
            if (upperQuery.startsWith("SELECT") || upperQuery.startsWith("WITH") || upperQuery.startsWith("FROM")
                    || upperQuery.startsWith("SHOW") || upperQuery.startsWith("DESCRIBE")
                    || upperQuery.startsWith("EXPLAIN") || upperQuery.startsWith("CALL")) {
                List<Map<String, Object>> results = duckDBService.executeQuery(query);
                return ResponseEntity.ok(Map.of(
                    "results", results,
                    "count", results.size()
                ));
            }
            duckDBService.executeStatement(query);
            return ResponseEntity.ok(Map.of(
                "message", "Statement executed successfully"
            ));
        } catch (SQLException e) {
            log.error("Error executing query", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to execute query",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Gets all rows of a table in the default in-memory database.
     *
     * @param tableName The name of the table to query
     */
    @GetMapping("/tables/{tableName}")
    public ResponseEntity<?> getTableData(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> results = duckDBService.executeQuery(
                    "SELECT * FROM " + DuckDBService.identifier(tableName));
            return ResponseEntity.ok(Map.of(
                "tableName", tableName,
                "results", results,
                "count", results.size()
            ));
        } catch (SQLException | IllegalArgumentException e) {
            log.error("Error fetching table data", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to fetch table data",
                "message", e.getMessage()
            ));
        }
    }
}
