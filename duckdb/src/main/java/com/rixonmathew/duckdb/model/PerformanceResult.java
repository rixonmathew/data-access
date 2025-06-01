package com.rixonmathew.duckdb.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Class to hold the results of performance comparison between
 * querying Parquet and DuckDB files.
 */
public class PerformanceResult {
    private final List<Duration> parquetQueryTimes;
    private final List<Duration> duckdbQueryTimes;
    private final Duration minParquetQueryTime;
    private final Duration maxParquetQueryTime;
    private final Duration avgParquetQueryTime;
    private final Duration minDuckdbQueryTime;
    private final Duration maxDuckdbQueryTime;
    private final Duration avgDuckdbQueryTime;
    private final List<Map<String, Object>> parquetResults;
    private final List<Map<String, Object>> duckdbResults;
    private final String query;
    private final boolean resultsMatch;
    private final int numRuns;

    public PerformanceResult(
            List<Duration> parquetQueryTimes,
            List<Duration> duckdbQueryTimes,
            List<Map<String, Object>> parquetResults,
            List<Map<String, Object>> duckdbResults,
            String query) {
        this.parquetQueryTimes = Collections.unmodifiableList(parquetQueryTimes);
        this.duckdbQueryTimes = Collections.unmodifiableList(duckdbQueryTimes);
        this.parquetResults = parquetResults;
        this.duckdbResults = duckdbResults;
        this.query = query;
        this.resultsMatch = compareResults(parquetResults, duckdbResults);
        this.numRuns = parquetQueryTimes.size();

        // Calculate min, max, and average for Parquet query times
        this.minParquetQueryTime = calculateMin(parquetQueryTimes);
        this.maxParquetQueryTime = calculateMax(parquetQueryTimes);
        this.avgParquetQueryTime = calculateAvg(parquetQueryTimes);

        // Calculate min, max, and average for DuckDB query times
        this.minDuckdbQueryTime = calculateMin(duckdbQueryTimes);
        this.maxDuckdbQueryTime = calculateMax(duckdbQueryTimes);
        this.avgDuckdbQueryTime = calculateAvg(duckdbQueryTimes);
    }

    /**
     * Calculate the minimum duration from a list of durations.
     */
    private Duration calculateMin(List<Duration> durations) {
        return durations.stream()
                .min(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    /**
     * Calculate the maximum duration from a list of durations.
     */
    private Duration calculateMax(List<Duration> durations) {
        return durations.stream()
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    /**
     * Calculate the average duration from a list of durations.
     */
    private Duration calculateAvg(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Duration.ZERO;
        }

        long totalMillis = durations.stream()
                .mapToLong(Duration::toMillis)
                .sum();

        return Duration.ofMillis(totalMillis / durations.size());
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
     * Returns the faster file format based on average query execution time.
     */
    public String getFasterFormat() {
        return avgParquetQueryTime.compareTo(avgDuckdbQueryTime) < 0 ? "Parquet" : "DuckDB";
    }

    /**
     * Returns the speed difference as a percentage based on average query times.
     * Positive value means DuckDB is faster, negative means Parquet is faster.
     */
    public double getSpeedDifferencePercent() {
        long parquetMillis = avgParquetQueryTime.toMillis();
        long duckdbMillis = avgDuckdbQueryTime.toMillis();

        if (parquetMillis == 0 || duckdbMillis == 0) {
            return 0.0; // Avoid division by zero
        }

        return ((double) (parquetMillis - duckdbMillis) / parquetMillis) * 100.0;
    }

    // Getters for query times
    public List<Duration> getParquetQueryTimes() {
        return parquetQueryTimes;
    }

    public List<Duration> getDuckdbQueryTimes() {
        return duckdbQueryTimes;
    }

    public Duration getMinParquetQueryTime() {
        return minParquetQueryTime;
    }

    public Duration getMaxParquetQueryTime() {
        return maxParquetQueryTime;
    }

    public Duration getAvgParquetQueryTime() {
        return avgParquetQueryTime;
    }

    public Duration getMinDuckdbQueryTime() {
        return minDuckdbQueryTime;
    }

    public Duration getMaxDuckdbQueryTime() {
        return maxDuckdbQueryTime;
    }

    public Duration getAvgDuckdbQueryTime() {
        return avgDuckdbQueryTime;
    }

    // For backward compatibility
    public Duration getParquetQueryTime() {
        return avgParquetQueryTime;
    }

    public Duration getDuckdbQueryTime() {
        return avgDuckdbQueryTime;
    }

    public int getNumRuns() {
        return numRuns;
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
                "Number of runs: " + numRuns + "\n" +
                "Parquet query time (min/avg/max): " + 
                    minParquetQueryTime.toMillis() + "/" + 
                    avgParquetQueryTime.toMillis() + "/" + 
                    maxParquetQueryTime.toMillis() + "ms\n" +
                "DuckDB query time (min/avg/max): " + 
                    minDuckdbQueryTime.toMillis() + "/" + 
                    avgDuckdbQueryTime.toMillis() + "/" + 
                    maxDuckdbQueryTime.toMillis() + "ms\n" +
                "Faster format (based on avg): " + getFasterFormat() + "\n" +
                "Speed difference (based on avg): " + String.format("%.2f%%", getSpeedDifferencePercent()) + "\n" +
                "Results match: " + resultsMatch + "\n" +
                "Number of results: " + parquetResults.size();
    }
}
