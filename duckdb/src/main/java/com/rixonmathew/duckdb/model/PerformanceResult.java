package com.rixonmathew.duckdb.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Class to hold the results of performance comparison between
 * querying Parquet and DuckDB files.
 */
public class PerformanceResult {
    private final Duration parquetQueryTime;
    private final Duration duckdbQueryTime;
    private final List<Map<String, Object>> parquetResults;
    private final List<Map<String, Object>> duckdbResults;
    private final String query;
    private final boolean resultsMatch;

    public PerformanceResult(
            Duration parquetQueryTime,
            Duration duckdbQueryTime,
            List<Map<String, Object>> parquetResults,
            List<Map<String, Object>> duckdbResults,
            String query) {
        this.parquetQueryTime = parquetQueryTime;
        this.duckdbQueryTime = duckdbQueryTime;
        this.parquetResults = parquetResults;
        this.duckdbResults = duckdbResults;
        this.query = query;
        this.resultsMatch = compareResults(parquetResults, duckdbResults);
    }

    /**
     * Compares the results from both queries to ensure they match.
     * This is a simple implementation that checks if the results have the same size
     * and contain the same elements (order-insensitive).
     */
    private boolean compareResults(List<Map<String, Object>> parquetResults, List<Map<String, Object>> duckdbResults) {
        if (parquetResults.size() != duckdbResults.size()) {
            return false;
        }
        
        // This is a simple comparison that might not work for all cases
        // A more robust implementation would compare each row and column
        return parquetResults.containsAll(duckdbResults) && duckdbResults.containsAll(parquetResults);
    }

    /**
     * Returns the faster file format based on query execution time.
     */
    public String getFasterFormat() {
        return parquetQueryTime.compareTo(duckdbQueryTime) < 0 ? "Parquet" : "DuckDB";
    }

    /**
     * Returns the speed difference as a percentage.
     * Positive value means DuckDB is faster, negative means Parquet is faster.
     */
    public double getSpeedDifferencePercent() {
        long parquetMillis = parquetQueryTime.toMillis();
        long duckdbMillis = duckdbQueryTime.toMillis();
        
        if (parquetMillis == 0 || duckdbMillis == 0) {
            return 0.0; // Avoid division by zero
        }
        
        return ((double) (parquetMillis - duckdbMillis) / parquetMillis) * 100.0;
    }

    // Getters
    public Duration getParquetQueryTime() {
        return parquetQueryTime;
    }

    public Duration getDuckdbQueryTime() {
        return duckdbQueryTime;
    }

    public List<Map<String, Object>> getParquetResults() {
        return parquetResults;
    }

    public List<Map<String, Object>> getDuckdbResults() {
        return duckdbResults;
    }

    public String getQuery() {
        return query;
    }

    public boolean isResultsMatch() {
        return resultsMatch;
    }

    @Override
    public String toString() {
        return "Performance Comparison Results:\n" +
                "Query: " + query + "\n" +
                "Parquet query time: " + parquetQueryTime.toMillis() + "ms\n" +
                "DuckDB query time: " + duckdbQueryTime.toMillis() + "ms\n" +
                "Faster format: " + getFasterFormat() + "\n" +
                "Speed difference: " + String.format("%.2f%%", getSpeedDifferencePercent()) + "\n" +
                "Results match: " + resultsMatch + "\n" +
                "Number of results: " + parquetResults.size();
    }
}