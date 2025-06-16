package com.rixonmathew.dataaccess.sqlite3.controller;

import com.rixonmathew.dataaccess.sqlite3.model.Employee;
import com.rixonmathew.dataaccess.sqlite3.service.QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for SQLite3 database operations.
 */
@RestController
@RequestMapping("/api/sqlite3")
public class Sqlite3Controller {

    private final QueryService queryService;

    public Sqlite3Controller(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Executes a SQL query against the SQLite3 database and returns the results.
     *
     * @param query The SQL query to execute
     * @return A list of Employee objects representing the query results
     */
    @GetMapping("/query")
    public List<Employee> query(@RequestParam String query) {
        return queryService.query(query);
    }

    /**
     * Returns all employees from the SQLite3 database.
     *
     * @return A list of all employees
     */
    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        return queryService.query("SELECT * FROM employees");
    }
}