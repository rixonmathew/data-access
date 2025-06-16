package com.rixonmathew.dataaccess.sqlite3.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test configuration for SQLite3 tests.
 * Provides a DataSource bean that uses a shared in-memory SQLite database.
 */
@TestConfiguration
public class TestConfig {

    /**
     * Creates a DataSource for testing that uses a shared in-memory SQLite database.
     *
     * @return A DataSource for testing
     * @throws SQLException If a database access error occurs
     */
    @Bean
    public DataSource dataSource() throws SQLException {
        // Create a shared in-memory SQLite database connection
        // Use a named in-memory database to ensure it's shared across connections
        Connection sharedConnection = DriverManager.getConnection("jdbc:sqlite:file:memdb?mode=memory&cache=shared");
        
        // Initialize the database with test data
        try (Statement stmt = sharedConnection.createStatement()) {
            // Create employees table
            stmt.execute("DROP TABLE IF EXISTS employees");
            stmt.execute("CREATE TABLE employees (" +
                         "id TEXT PRIMARY KEY, " +
                         "first_name TEXT, " +
                         "last_name TEXT, " +
                         "email TEXT, " +
                         "department TEXT" +
                         ")");
            
            // Insert test data
            stmt.execute("INSERT INTO employees VALUES " +
                         "('1', 'John', 'Doe', 'john.doe@example.com', 'Engineering'), " +
                         "('2', 'Jane', 'Smith', 'jane.smith@example.com', 'Marketing'), " +
                         "('3', 'Bob', 'Johnson', 'bob.johnson@example.com', 'Finance'), " +
                         "('4', 'Alice', 'Williams', 'alice.williams@example.com', 'HR'), " +
                         "('5', 'Charlie', 'Brown', 'charlie.brown@example.com', 'Engineering')");
        }
        
        // Create a data source that uses the shared connection
        // Set suppressClose to true to prevent the connection from being closed
        return new SingleConnectionDataSource(sharedConnection, true);
    }
}