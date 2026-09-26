package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.config.DuckLakeProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ducklake extension is downloaded at runtime, so startup checks the loaded build against
 * {@code ducklake.expected-extension-version}. Uses a local DuckDB-file catalog; no containers needed.
 * Skipped when the extensions cannot be installed (e.g. no access to extensions.duckdb.org).
 */
class DuckLakeExtensionVersionTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void requireExtensions() {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL httpfs");
            stmt.execute("INSTALL ducklake");
        } catch (SQLException e) {
            assumeTrue(false, "DuckDB extensions unavailable: " + e.getMessage());
        }
    }

    @Test
    void testDefaultExpectedVersionMatchesLoadedExtension() throws SQLException {
        DuckLakeProperties properties = localProperties();

        assertThat(properties.getExpectedExtensionVersion()).isNotBlank();
        startAndStop(properties);
    }

    @Test
    void testMismatchedExtensionVersionFailsStartup() throws SQLException {
        DuckLakeProperties properties = localProperties();
        properties.setExpectedExtensionVersion("00000000");

        DuckDBService duckDB = new DuckDBService(properties);
        try {
            assertThatThrownBy(() -> new DuckLakeService(duckDB, properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tested with 00000000")
                    .hasMessageContaining("ducklake.expected-extension-version");
        } finally {
            duckDB.destroy();
        }
    }

    @Test
    void testEmptyExpectedVersionSkipsCheck() throws SQLException {
        DuckLakeProperties properties = localProperties();
        properties.setExpectedExtensionVersion("");

        startAndStop(properties);
    }

    private void startAndStop(DuckLakeProperties properties) throws SQLException {
        DuckDBService duckDB = new DuckDBService(properties);
        try {
            DuckLakeService duckLake = new DuckLakeService(duckDB, properties);
            assertThat(duckLake.currentSnapshotId()).isGreaterThanOrEqualTo(0);
        } finally {
            duckDB.destroy();
        }
    }

    private DuckLakeProperties localProperties() {
        DuckLakeProperties properties = new DuckLakeProperties();
        properties.getCatalog().setPath(tempDir.resolve("catalog.ducklake").toString());
        properties.setDataPath(tempDir.resolve("lake").toString() + "/");
        return properties;
    }
}
