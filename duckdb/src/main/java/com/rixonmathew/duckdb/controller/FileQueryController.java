package com.rixonmathew.duckdb.controller;

import com.rixonmathew.duckdb.model.PerformanceResult;
import com.rixonmathew.duckdb.service.FileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/file-query")
public class FileQueryController {

    private final FileQueryService fileQueryService;

    public FileQueryController(FileQueryService fileQueryService) {
        this.fileQueryService = fileQueryService;
    }

    @GetMapping("/parquet")
    public List<Map<String, Object>> queryParquetFile(
            @RequestParam String bucket,
            @RequestParam String key,
            @RequestParam String query) {
        return fileQueryService.queryParquetFile(bucket, key, query);
    }

    @GetMapping("/duckdb")
    public List<Map<String, Object>> queryDuckDBFile(
            @RequestParam String bucket,
            @RequestParam String key,
            @RequestParam String query) {
        return fileQueryService.queryDuckDBFile(bucket, key, query);
    }

    @GetMapping("/compare")
    public PerformanceResult comparePerformance(
            @RequestParam String bucket,
            @RequestParam String parquetKey,
            @RequestParam String duckdbKey,
            @RequestParam String query) {
        return fileQueryService.compareQueryPerformance(bucket, parquetKey, duckdbKey, query);
    }
}