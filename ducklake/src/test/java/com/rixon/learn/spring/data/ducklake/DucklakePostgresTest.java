package com.rixon.learn.spring.data.ducklake;

import com.rixon.learn.spring.data.ducklake.service.DuckDBService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the DuckLake catalog that {@link DuckDBService} attaches on startup:
 * table data is queryable through DuckDB, one Parquet file is written per partition,
 * and the table metadata is persisted in the PostgreSQL catalog database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
public class DucklakePostgresTest {

    @Autowired
    private DuckDBService duckDBService;

    @Autowired
    private PostgreSQLContainer<?> postgresContainer;

    @Test
    public void testSimpleDucklakeTableQuery() throws Exception {
        List<Map<String, Object>> rows = duckDBService.executeQuery(
                "SELECT id, product, region FROM pg_ducklake.sales_data ORDER BY id");
        assertEquals(3, rows.size());
        assertEquals("apple", rows.get(0).get("product"));
        assertEquals("eastern", rows.get(2).get("region"));

        // Small inserts are inlined into the catalog database by DuckLake; flush them to Parquet.
        // Partitioned by (year(sale_date), region): three regions -> three data files
        duckDBService.executeQuery("CALL ducklake_flush_inlined_data('pg_ducklake')");
        List<Map<String, Object>> files = duckDBService.executeQuery(
                "SELECT data_file FROM ducklake_list_files('pg_ducklake', 'sales_data')");
        assertEquals(3, files.size());

        // DuckLake metadata lives in PostgreSQL, not in DuckDB
        try (Connection conn = DriverManager.getConnection(postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(), postgresContainer.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT count(*) FROM ducklake_table WHERE table_name = 'sales_data'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
