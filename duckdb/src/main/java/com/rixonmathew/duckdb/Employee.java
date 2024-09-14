package com.rixonmathew.duckdb;

public record Employee(
        String id,
        String firstName,
        String lastName,
        String email,
        String position
) {
}