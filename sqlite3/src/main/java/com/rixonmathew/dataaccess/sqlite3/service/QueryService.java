package com.rixonmathew.dataaccess.sqlite3.service;

import com.rixonmathew.dataaccess.sqlite3.model.Employee;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for querying the SQLite3 database.
 */
@Service
public class QueryService {

    private final DataSource dataSource;

    public QueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Executes a SQL query against the SQLite3 database and returns the results as a list of Employee objects.
     *
     * @param query The SQL query to execute
     * @return A list of Employee objects representing the query results
     */
    public List<Employee> query(String query) {
        List<Employee> employees = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                try (java.sql.ResultSet rs = stmt.executeQuery(query)) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String firstName = rs.getString("first_name");
                        String lastName = rs.getString("last_name");
                        String email = rs.getString("email");
                        String department = rs.getString("department");

                        Employee employee = new Employee(id, firstName, lastName, email, department);
                        employees.add(employee);
                    }
                    return employees;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return employees;
        }
    }

    /**
     * Creates a connection to an embedded SQLite database file.
     * 
     * @param dbFilePath The path to the SQLite database file
     * @return A connection to the SQLite database
     * @throws SQLException If a database access error occurs
     */
    public Connection createConnectionToEmbeddedDb(String dbFilePath) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
    }
}
