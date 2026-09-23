package com.rixon.learn.spring.data.ducklake.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "duckdb")
public class DuckDBConfig {
    private String catalogType = "memory"; // Default to memory
    private String host;
    private int port;
    private String database;
    private String catalogJdbcUrl;
    private String catalogUsername;
    private String catalogPassword;
    private String catalogDataFilesPath;
}