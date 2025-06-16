package com.rixonmathew.dataaccess.sqlite3.model;

/**
 * Employee record representing an employee in the SQLite3 database.
 */
public record Employee(
        String id,
        String firstName,
        String lastName,
        String email,
        String department
) {
}