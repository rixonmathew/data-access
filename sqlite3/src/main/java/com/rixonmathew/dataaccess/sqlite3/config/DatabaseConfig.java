package com.rixonmathew.dataaccess.sqlite3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the SQLite3 database.
 */
@Configuration
public class DatabaseConfig {

    private static final String DB_FILE_NAME = "sqlite3-db.db";
    private static final String SCHEMA_SQL = "schema.sql";

    /**
     * Creates a DataSource for the SQLite3 database.
     * If the database file doesn't exist, it will be created and initialized with the schema.
     *
     * @return A DataSource for the SQLite3 database
     */
    @Bean
    public DataSource dataSource() {
        // Create a temporary directory for the database file if it doesn't exist
        Path dbDir = Paths.get(System.getProperty("java.io.tmpdir"), "sqlite3");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory for SQLite database", e);
        }

        // Create the database file path
        Path dbPath = dbDir.resolve(DB_FILE_NAME);
        File dbFile = dbPath.toFile();

        // Create the data source
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // Initialize the database if the file doesn't exist
        if (!dbFile.exists()) {
            DatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_SQL));
            DatabasePopulatorUtils.execute(populator, dataSource);
        }

        return dataSource;
    }
}