package com.rixonmathew.duckdb;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

@Component
public class QueryService {

    public List<Employee> query(String query) {

        List<Employee> employees = new ArrayList<>();
        try (org.duckdb.DuckDBConnection conn = (org.duckdb.DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:")) {
            createAndFillEmployeesTable(conn);
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
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return employees;
        }
    }


    public void createAndFillEmployeesTable(Connection connection) {
        String createTableQuery = "CREATE TABLE employees (" +
                                  "id VARCHAR PRIMARY KEY, " +
                                  "first_name VARCHAR, " +
                                  "last_name VARCHAR, " +
                                  "email VARCHAR, " +
                                  "department VARCHAR" +
                                  ");";

        String insertQueryTemplate = "INSERT INTO employees (id, first_name, last_name, email, department) VALUES ";

        try (java.sql.Statement stmt = connection.createStatement()) {
            // Create table
            stmt.executeUpdate(createTableQuery);

            // Fill table with 1000 random employees
            StringBuilder insertQuery = new StringBuilder(insertQueryTemplate);
            for (int i = 1; i <= 1000; i++) {
                String id = java.util.UUID.randomUUID().toString();
                String firstName = "FirstName" + i;
                String lastName = "LastName" + i;
                String email = "employee" + i + "@example.com";
                String department = "Department" + ((i % 10) + 1);
                insertQuery.append(String.format("('%s', '%s', '%s', '%s', '%s')", id, firstName, lastName, email, department));

                if (i < 1000) {
                    insertQuery.append(", ");
                } else {
                    insertQuery.append(";");
                }
            }
            stmt.executeUpdate(insertQuery.toString());
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }
}
