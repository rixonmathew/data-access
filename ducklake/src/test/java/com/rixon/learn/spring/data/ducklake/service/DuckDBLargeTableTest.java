package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class DuckDBLargeTableTest {

    @Autowired
    private DuckDBService duckDBService;

    private static final String TEST_TABLE = "large_test_table";
    private static final int NUM_ROWS = 1000;
    private static final int NUM_COLUMNS = 100;

    @BeforeEach
    void setUp() throws SQLException {
        // Create a large test table with 1000 rows and 100 columns
        duckDBService.createLargeTestTable(TEST_TABLE, NUM_ROWS, NUM_COLUMNS);
    }

    @AfterEach
    void tearDown() throws SQLException {
        duckDBService.executeStatement("DROP TABLE IF EXISTS " + TEST_TABLE);
    }

    @Test
    void testCountQuery() throws SQLException {
        // Test count query
        List<Map<String, Object>> countResult = duckDBService.executeQuery("SELECT COUNT(*) as count FROM " + TEST_TABLE);
        assertEquals(1, countResult.size());
        assertEquals(String.valueOf(NUM_ROWS), String.valueOf(countResult.get(0).get("count")));
    }

    @Test
    void testSelectQuery() throws SQLException {
        // Test select query with limit
        List<Map<String, Object>> selectResult = duckDBService.executeQuery("SELECT * FROM " + TEST_TABLE + " LIMIT 10");
        assertEquals(10, selectResult.size());

        // Verify first row has all columns
        Map<String, Object> firstRow = selectResult.get(0);
        assertEquals(NUM_COLUMNS + 1, firstRow.size()); // +1 for id column
        assertEquals("1", String.valueOf(firstRow.get("id")));
        assertEquals("value_1_1", firstRow.get("attr1"));
        assertEquals("value_1_100", firstRow.get("attr100"));
    }

    @Test
    void testFilterQuery() throws SQLException {
        // Test filter query
        List<Map<String, Object>> filterResult = duckDBService.executeQuery(
            "SELECT * FROM " + TEST_TABLE + " WHERE id = 500");
        assertEquals(1, filterResult.size());

        Map<String, Object> row = filterResult.get(0);
        assertEquals("500", String.valueOf(row.get("id")));
        assertEquals("value_500_1", row.get("attr1"));
        assertEquals("value_500_100", row.get("attr100"));
    }

    @Test
    void testAggregateQuery() throws SQLException {
        // Test aggregate query
        List<Map<String, Object>> aggregateResult = duckDBService.executeQuery(
            "SELECT MIN(id) as min_id, MAX(id) as max_id, COUNT(*) as count FROM " + TEST_TABLE);
        assertEquals(1, aggregateResult.size());

        Map<String, Object> result = aggregateResult.get(0);
        assertEquals("1", String.valueOf(result.get("min_id")));
        assertEquals(String.valueOf(NUM_ROWS), String.valueOf(result.get("max_id")));
        assertEquals(String.valueOf(NUM_ROWS), String.valueOf(result.get("count")));
    }
}
