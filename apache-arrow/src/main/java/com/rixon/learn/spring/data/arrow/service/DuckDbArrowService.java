package com.rixon.learn.spring.data.arrow.service;

import com.rixon.learn.spring.data.arrow.config.ArrowProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.c.ArrowArrayStream;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBResultSet;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Embedded DuckDB exchanging data with Arrow through the Arrow C Data Interface: query results are exported
 * as Arrow record batches, and Arrow streams are registered as views DuckDB can query, without copying rows.
 * <p>
 * Startup creates a deterministic {@code trades} table ({@link ArrowProperties#getSampleRows()} rows) used by
 * the Flight and Flight SQL servers.
 */
@Slf4j
@Service
public class DuckDbArrowService implements DisposableBean {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DuckDBConnection root;
    private final int batchSize;

    public DuckDbArrowService(ArrowProperties properties) throws SQLException {
        this.root = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
        this.batchSize = properties.getBatchSize();
        try (Statement stmt = root.createStatement()) {
            stmt.execute("""
                    CREATE TABLE trades AS
                    SELECT 'T-' || lpad(i::VARCHAR, 7, '0')                          AS trade_id,
                           ['AAPL', 'AMZN', 'GOOG', 'MSFT', 'NVDA'][(i % 5) + 1]     AS ticker,
                           (100 + (i % 500) + (i % 100) / 100.0)::DECIMAL(12, 2)     AS price,
                           ((i % 100) + 1)::BIGINT                                   AS quantity,
                           DATE '2026-01-01' + (i % 90)::INTEGER                     AS trade_date,
                           CASE WHEN i % 4 = 0 THEN NULL ELSE ['NASDAQ', 'NYSE'][(i % 2) + 1] END AS venue
                    FROM range(1, ?) t(i)
                    ORDER BY i""".replace("?", String.valueOf(properties.getSampleRows() + 1L)));
        }
        log.info("DuckDB trades table created with {} rows", properties.getSampleRows());
    }

    /** A new connection to the shared in-memory database. The caller closes it. */
    public Connection openConnection() throws SQLException {
        return root.duplicate();
    }

    /** Runs a query and exposes the result as Arrow batches of the configured batch size. */
    public ArrowQuery query(String sql, BufferAllocator allocator) throws SQLException {
        return query(sql, List.of(), allocator);
    }

    /**
     * Runs a parameterized query and exposes the result as Arrow batches. DuckDB fills each batch
     * directly in Arrow memory owned by {@code allocator}; no rows pass through JDBC getters.
     */
    public ArrowQuery query(String sql, List<Object> parameters, BufferAllocator allocator) throws SQLException {
        Connection conn = openConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            DuckDBResultSet rs = (DuckDBResultSet) stmt.executeQuery();
            ArrowReader reader = (ArrowReader) rs.arrowExportStream(allocator, batchSize);
            return new ArrowQuery(conn, stmt, rs, reader);
        } catch (SQLException | RuntimeException e) {
            conn.close();
            throw e;
        }
    }

    /**
     * Registers an Arrow stream as a DuckDB view, runs {@code work} on the same connection, then drops the view.
     * <p>
     * Ownership: this call takes ownership of {@code reader} and closes it (through the exported stream).
     * The view can be scanned <em>once</em>: an Arrow stream is consumed as it is read.
     */
    public <T> T withArrowView(String viewName, ArrowReader reader, BufferAllocator allocator,
                               SqlFunction<Connection, T> work) throws SQLException {
        String view = identifier(viewName);
        ArrowArrayStream stream = ArrowArrayStream.allocateNew(allocator);
        try (Connection conn = openConnection()) {
            // From here the stream owns the reader: releasing the stream closes the reader
            Data.exportArrayStream(allocator, reader, stream);
            ((DuckDBConnection) conn).registerArrowStream(view, stream);
            try {
                return work.apply(conn);
            } finally {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP VIEW IF EXISTS " + view);
                }
            }
        } finally {
            // DuckDB releases the stream once a scan finishes; release it here only if it was never read
            if (stream.snapshot().release != 0) {
                stream.release();
            }
            stream.close();
        }
    }

    private static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid identifier: " + name);
        }
        return name;
    }

    @Override
    public void destroy() throws SQLException {
        root.close();
    }

    /** An executing DuckDB query exposed as an {@link ArrowReader}. Closing it closes the reader and the connection. */
    public record ArrowQuery(Connection connection, Statement statement, DuckDBResultSet resultSet, ArrowReader reader)
            implements AutoCloseable {

        public VectorSchemaRoot root() throws java.io.IOException {
            return reader.getVectorSchemaRoot();
        }

        @Override
        public void close() throws Exception {
            try (connection; statement; resultSet; reader) {
                // closed in reverse order: reader, result set, statement, connection
            }
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }
}
