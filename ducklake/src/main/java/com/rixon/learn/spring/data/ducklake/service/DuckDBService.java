package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.config.DuckLakeProperties;
import lombok.extern.slf4j.Slf4j;
import org.duckdb.DuckDBConnection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Owns the single embedded in-memory DuckDB instance. Each call gets its own connection
 * ({@link DuckDBConnection#duplicate()}) to the same instance, so attached catalogs and secrets
 * are shared while statements from different threads stay independent.
 */
@Slf4j
@Service
public class DuckDBService implements DisposableBean {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DuckDBConnection root;

    public DuckDBService(DuckLakeProperties properties) throws SQLException {
        this.root = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
        try (Statement stmt = root.createStatement()) {
            stmt.execute("INSTALL httpfs");
            stmt.execute("LOAD httpfs");
            DuckLakeProperties.S3 s3 = properties.getS3();
            if (StringUtils.hasText(s3.getEndpoint())) {
                stmt.execute("""
                        CREATE OR REPLACE SECRET s3_storage (
                            TYPE s3,
                            KEY_ID %s,
                            SECRET %s,
                            REGION %s,
                            ENDPOINT %s,
                            URL_STYLE %s,
                            USE_SSL %s)
                        """.formatted(literal(s3.getAccessKeyId()), literal(s3.getSecretAccessKey()),
                        literal(s3.getRegion()), literal(s3.getEndpoint()), literal(s3.getUrlStyle()), s3.isUseSsl()));
                log.info("DuckDB S3 secret created for endpoint {}", s3.getEndpoint());
            }
        }
    }

    /** Opens a new connection to the shared DuckDB instance. The caller closes it. */
    public Connection openConnection() throws SQLException {
        return root.duplicate();
    }

    /**
     * Runs {@code work} inside one transaction on one connection: commits if it returns,
     * rolls back and rethrows if it throws.
     */
    public <T> T inTransaction(SqlFunction<Connection, T> work) throws SQLException {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Creates (or replaces) a table from a local file or an {@code s3://} object.
     *
     * @param format CSV or PARQUET
     */
    public void createTableFromFile(String tableName, String path, String format) throws SQLException {
        String readFunction = switch (format.toUpperCase()) {
            case "CSV" -> "read_csv_auto";
            case "PARQUET" -> "read_parquet";
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
        executeStatement("CREATE OR REPLACE TABLE %s AS SELECT * FROM %s(%s)"
                .formatted(identifier(tableName), readFunction, literal(path)));
        log.info("Table {} created from {} ({})", tableName, path, format);
    }

    public void createTableFromCSV(String tableName, String csvPath) throws SQLException {
        createTableFromFile(tableName, csvPath, "CSV");
    }

    /** Creates a table with an INTEGER id and {@code columns} VARCHAR attributes, generated in SQL. */
    public void createLargeTestTable(String tableName, int rows, int columns) throws SQLException {
        StringBuilder select = new StringBuilder("SELECT i::INTEGER AS id");
        for (int j = 1; j <= columns; j++) {
            select.append(", 'value_' || i || '_").append(j).append("' AS attr").append(j);
        }
        select.append(" FROM range(1, ").append(rows + 1).append(") t(i)");
        executeStatement("CREATE OR REPLACE TABLE " + identifier(tableName) + " AS " + select);
        log.info("Large test table {} created with {} rows and {} columns", tableName, rows, columns);
    }

    public void executeStatement(String sql) throws SQLException {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /** Executes a query and returns each row as a column-name to value map, in column order. */
    public List<Map<String, Object>> executeQuery(String query) throws SQLException {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return toRows(rs);
        }
    }

    static List<Map<String, Object>> toRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Validates a bare SQL identifier (table, column or catalog name) so it can be spliced into SQL. */
    public static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid identifier: " + name);
        }
        return name;
    }

    /** Quotes a value as a SQL string literal. */
    public static String literal(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
    }

    @Override
    public void destroy() throws SQLException {
        root.close();
        log.info("DuckDB connection closed");
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }
}
