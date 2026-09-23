package com.rixon.learn.spring.data.ducklake.controller;

import com.rixon.learn.spring.data.ducklake.service.DuckDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/duckdb")
@RequiredArgsConstructor
public class DuckDBController {

    private final DuckDBService duckDBService;

    /**
     * Creates a table from S3 data
     *
     * @param tableName The name of the table to create
     * @param s3Path The S3 path to the data
     * @param format The format of the data (e.g., CSV, Parquet)
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/tables")
    public ResponseEntity<?> createTable(
            @RequestParam String tableName,
            @RequestParam String s3Path,
            @RequestParam String format) {

        try {
            duckDBService.createTableFromS3(tableName, s3Path, format);
            return ResponseEntity.ok(Map.of(
                "message", "Table created successfully",
                "tableName", tableName,
                "s3Path", s3Path
            ));
        } catch (SQLException e) {
            log.error("Error creating table from S3", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to create table",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Executes a SQL query against DuckDB
     *
     * @param query The SQL query to execute
     * @return ResponseEntity with query results or error message
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody String query) {
        try {
            // Determine if this is a query that returns results or a statement
            String upperQuery = query.trim().toUpperCase();
            if (upperQuery.startsWith("SELECT") || upperQuery.startsWith("SHOW") || 
                upperQuery.startsWith("DESCRIBE") || upperQuery.startsWith("EXPLAIN")) {
                // This is a query that returns results
                List<Map<String, Object>> results = duckDBService.executeQuery(query);
                return ResponseEntity.ok(Map.of(
                    "results", results,
                    "count", results.size()
                ));
            } else {
                // This is a statement that doesn't return results
                duckDBService.executeStatement(query);
                return ResponseEntity.ok(Map.of(
                    "message", "Statement executed successfully"
                ));
            }
        } catch (SQLException e) {
            log.error("Error executing query", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to execute query",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Gets all data from a table
     *
     * @param tableName The name of the table to query
     * @return ResponseEntity with table data or error message
     */
    @GetMapping("/tables/{tableName}")
    public ResponseEntity<?> getTableData(@PathVariable String tableName) {
        try {
            String query = String.format("SELECT * FROM %s", tableName);
            List<Map<String, Object>> results = duckDBService.executeQuery(query);
            return ResponseEntity.ok(Map.of(
                "tableName", tableName,
                "results", results,
                "count", results.size()
            ));
        } catch (SQLException e) {
            log.error("Error fetching table data", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to fetch table data",
                "message", e.getMessage()
            ));
        }
    }
}
