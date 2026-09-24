package com.rixon.learn.spring.data.ducklake.controller;

import com.rixon.learn.spring.data.ducklake.model.DuckLakeDataFile;
import com.rixon.learn.spring.data.ducklake.model.DuckLakeSnapshot;
import com.rixon.learn.spring.data.ducklake.model.Trade;
import com.rixon.learn.spring.data.ducklake.service.DuckLakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Trades tables in the DuckLake catalog: append (one snapshot per request), read current or
 * historical versions, and inspect snapshots and data files.
 */
@RestController
@RequestMapping("/api/ducklake")
@RequiredArgsConstructor
public class DuckLakeController {

    private final DuckLakeService duckLakeService;

    /** Appends trades, creating the table (partitioned by ticker) on first use. */
    @PostMapping("/tables/{table}/trades")
    public Map<String, Object> appendTrades(@PathVariable String table, @RequestBody List<Trade> trades) throws SQLException {
        duckLakeService.createTradesTable(table, List.of("ticker"));
        long snapshotId = duckLakeService.appendTrades(table, trades);
        return Map.of("table", table, "appended", trades.size(), "snapshotId", snapshotId);
    }

    /** Current trades, or the trades at {@code version} (time travel). */
    @GetMapping("/tables/{table}/trades")
    public List<Trade> findTrades(@PathVariable String table, @RequestParam(required = false) Long version) throws SQLException {
        return duckLakeService.findTrades(table, version);
    }

    @GetMapping("/tables/{table}/files")
    public List<DuckLakeDataFile> listDataFiles(@PathVariable String table) throws SQLException {
        return duckLakeService.listDataFiles(table);
    }

    @GetMapping("/snapshots")
    public List<DuckLakeSnapshot> listSnapshots() throws SQLException {
        return duckLakeService.listSnapshots();
    }

    @ExceptionHandler({SQLException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
