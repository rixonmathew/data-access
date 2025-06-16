package com.rixonmathew.dataaccess.sqlite3.service;

import com.rixonmathew.dataaccess.sqlite3.model.Employee;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the QueryService.
 * Uses a shared in-memory SQLite database for all tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QueryServiceIntegrationTest {

    private QueryService queryService;
    private DataSource dataSource;
    private Connection sharedConnection;

    @BeforeAll
    public void setUpDatabase() throws SQLException {
        // Create a shared in-memory SQLite database connection
        // Use a named in-memory database to ensure it's shared across connections
        sharedConnection = DriverManager.getConnection("jdbc:sqlite:file:memdb?mode=memory&cache=shared");

        // Create a data source that uses the shared connection
        // Set suppressClose to true to prevent the connection from being closed
        dataSource = new SingleConnectionDataSource(sharedConnection, true);
        queryService = new QueryService(dataSource);

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
    }

    @Test
    public void testQueryAllEmployees() {
        // Execute a query to get all employees
        List<Employee> employees = queryService.query("SELECT * FROM employees");

        // Verify that employees were returned
        assertNotNull(employees);
        assertFalse(employees.isEmpty());

        // Verify that the sample data from schema.sql is present
        assertEquals(5, employees.size());

        // Verify the first employee
        Employee firstEmployee = employees.get(0);
        assertEquals("1", firstEmployee.id());
        assertEquals("John", firstEmployee.firstName());
        assertEquals("Doe", firstEmployee.lastName());
        assertEquals("john.doe@example.com", firstEmployee.email());
        assertEquals("Engineering", firstEmployee.department());
    }

    @Test
    public void testQueryWithFilter() {
        // Execute a query with a filter
        List<Employee> engineers = queryService.query(
                "SELECT * FROM employees WHERE department = 'Engineering'");

        // Verify that only engineers were returned
        assertNotNull(engineers);
        assertEquals(2, engineers.size());

        // Verify all returned employees are engineers
        for (Employee engineer : engineers) {
            assertEquals("Engineering", engineer.department());
        }
    }

    @Test
    public void testQueryWithJoin() {
        // Create a temporary table for departments
        queryService.query(
                "CREATE TEMPORARY TABLE departments (id TEXT PRIMARY KEY, name TEXT)");

        // Insert some departments
        queryService.query(
                "INSERT INTO departments VALUES ('1', 'Engineering'), ('2', 'Marketing'), " +
                "('3', 'Finance'), ('4', 'HR')");

        // Execute a query with a join
        List<Employee> employees = queryService.query(
                "SELECT e.id, e.first_name, e.last_name, e.email, d.name as department " +
                "FROM employees e " +
                "JOIN departments d ON e.department = d.name " +
                "WHERE d.name = 'Engineering'");

        // Verify that only engineers were returned
        assertNotNull(employees);
        assertEquals(2, employees.size());

        // Verify all returned employees are engineers
        for (Employee engineer : employees) {
            assertEquals("Engineering", engineer.department());
        }
    }
}
