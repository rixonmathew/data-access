package com.rixonmathew.duckdb.service;

import com.rixonmathew.duckdb.model.PerformanceResult;

import java.util.List;
import java.util.Map;

/**
 * Service interface for querying data from different file formats (Parquet and DuckDB)
 * and comparing their performance.
 */
public interface FileQueryService {

    /**
     * Executes a query against a Parquet file in S3.
     *
     * @param s3Bucket The S3 bucket name
     * @param s3Key The S3 object key (path to the Parquet file)
     * @param query The SQL query to execute
     * @return List of query results as maps
     */
    List<Map<String, Object>> queryParquetFile(String s3Bucket, String s3Key, String query);

    /**
     * Executes a query against a DuckDB file in S3.
     *
     * @param s3Bucket The S3 bucket name
     * @param s3Key The S3 object key (path to the DuckDB file)
     * @param query The SQL query to execute
     * @return List of query results as maps
     */
    List<Map<String, Object>> queryDuckDBFile(String s3Bucket, String s3Key, String query);

    /**
     * Compares the performance of querying a Parquet file versus a DuckDB file.
     * This is a convenience method that runs the query once.
     *
     * @param s3Bucket The S3 bucket name
     * @param parquetKey The S3 object key for the Parquet file
     * @param duckdbKey The S3 object key for the DuckDB file
     * @param query The SQL query to execute on both files
     * @return A performance comparison result
     */
    default PerformanceResult compareQueryPerformance(String s3Bucket, String parquetKey, String duckdbKey, String query) {
        return compareQueryPerformance(s3Bucket, parquetKey, duckdbKey, query, 1);
    }

    /**
     * Compares the performance of querying a Parquet file versus a DuckDB file.
     * Runs the query multiple times and reports min, max, and average query times.
     *
     * @param s3Bucket The S3 bucket name
     * @param parquetKey The S3 object key for the Parquet file
     * @param duckdbKey The S3 object key for the DuckDB file
     * @param query The SQL query to execute on both files
     * @param numRuns The number of times to run the query
     * @return A performance comparison result with statistics
     */
    PerformanceResult compareQueryPerformance(String s3Bucket, String parquetKey, String duckdbKey, String query, int numRuns);
}
