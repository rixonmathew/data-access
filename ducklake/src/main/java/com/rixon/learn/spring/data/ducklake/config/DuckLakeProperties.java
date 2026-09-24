package com.rixon.learn.spring.data.ducklake.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the embedded DuckDB engine, the DuckLake catalog it attaches, and the
 * optional S3 endpoint used for DuckLake data files and ad-hoc {@code s3://} reads.
 */
@Data
@ConfigurationProperties(prefix = "ducklake")
public class DuckLakeProperties {

    /** Name the DuckLake catalog is attached under; tables are addressed as {@code <alias>.<table>}. */
    private String catalogAlias = "lake";

    /** Where DuckLake writes Parquet data files: a local directory or an {@code s3://bucket/prefix/} URL. */
    private String dataPath = "data_files/lake/";

    /**
     * Inserts with at most this many rows are stored in the catalog database instead of Parquet
     * until {@code ducklake_flush_inlined_data} runs. {@code null} keeps the DuckLake default; 0 disables inlining.
     */
    private Integer dataInliningRowLimit;

    private Catalog catalog = new Catalog();

    private S3 s3 = new S3();

    public enum CatalogType { DUCKDB, POSTGRES }

    @Data
    public static class Catalog {
        /** DUCKDB keeps the metadata in a local DuckDB file; POSTGRES keeps it in a PostgreSQL database. */
        private CatalogType type = CatalogType.DUCKDB;
        /** Metadata file for the DUCKDB catalog type. */
        private String path = "data_files/catalog.ducklake";
        private String host;
        private int port = 5432;
        private String database;
        private String username;
        private String password;
    }

    @Data
    public static class S3 {
        /** host:port of an S3-compatible endpoint (e.g. LocalStack). Leave empty to skip creating an S3 secret. */
        private String endpoint;
        private String region = "us-east-1";
        private String accessKeyId;
        private String secretAccessKey;
        private String urlStyle = "path";
        private boolean useSsl = false;
    }
}
