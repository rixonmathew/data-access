package com.rixonmathew.duckdb;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryServiceTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(QueryServiceTest.class);

    private QueryService queryService;

    @BeforeEach
    public void setUp() {
        queryService = new QueryService();
    }

    @Test
    public void testQuery() {

        List<Employee> employees = queryService.query("select * from employees");
        assertEquals(1000,employees.size());
    }

}