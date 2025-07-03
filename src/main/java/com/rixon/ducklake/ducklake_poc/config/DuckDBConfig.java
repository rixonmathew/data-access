package com.rixon.ducklake.ducklake_poc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "duckdb")
public class DuckDBConfig {
    private String catalogType = "memory"; // Default to memory
    private String catalogJdbcUrl;
    private String catalogUsername;
    private String catalogPassword;
}