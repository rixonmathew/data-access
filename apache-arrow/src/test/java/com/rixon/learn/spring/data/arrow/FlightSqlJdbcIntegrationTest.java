package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.flightsql.DuckDbFlightSqlProducer;
import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import org.apache.arrow.flight.FlightServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A plain JDBC client talking to DuckDB through Arrow Flight SQL: the Flight SQL JDBC driver sends SQL over
 * gRPC and receives the results as Arrow record batches, which it exposes through the JDBC API.
 */
@ArrowIntegrationTest
class FlightSqlJdbcIntegrationTest {

    @Autowired
    @Qualifier("flightSqlServer")
    private FlightServer server;

    @Autowired
    private DuckDbFlightSqlProducer producer;

    @Autowired
    private DuckDbArrowService duckDB;

    private String url(String password) {
        return "jdbc:arrow-flight-sql://localhost:%d/?useEncryption=false&user=arrow&password=%s"
                .formatted(server.getPort(), password);
    }

    @Test
    void testQueryResultsAndTypesMatchDuckDb() throws SQLException {
        String sql = "SELECT ticker, count(*) AS trades, sum(quantity) AS quantity, sum(price) AS total, "
                + "min(trade_date) AS first_day FROM trades GROUP BY ticker ORDER BY ticker";
        try (Connection conn = DriverManager.getConnection(url("arrow"));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            assertThat(md.getColumnType(1)).isEqualTo(Types.VARCHAR);
            assertThat(md.getColumnType(3)).isEqualTo(Types.DECIMAL);           // DuckDB sum(BIGINT) is HUGEINT -> DECIMAL(38,0)
            assertThat(md.getColumnType(4)).isEqualTo(Types.DECIMAL);
            assertThat(md.getColumnType(5)).isEqualTo(Types.DATE);

            List<String> viaFlight = rows(rs);
            assertThat(viaFlight).hasSize(5).isEqualTo(direct(sql));
        }
    }

    @Test
    void testPreparedStatementBindsParametersOnTheServer() throws SQLException {
        String sql = "SELECT count(*) AS n, sum(quantity) AS q FROM trades WHERE ticker = ? AND quantity > ? AND trade_date >= ?";
        Date from = Date.valueOf(LocalDate.of(2026, 2, 1));
        try (Connection flight = DriverManager.getConnection(url("arrow"));
             PreparedStatement viaFlight = flight.prepareStatement(sql);
             Connection local = duckDB.openConnection();
             PreparedStatement direct = local.prepareStatement(sql)) {
            for (String ticker : List.of("AAPL", "NVDA")) {
                for (PreparedStatement stmt : List.of(viaFlight, direct)) {
                    stmt.setString(1, ticker);
                    stmt.setLong(2, 90);
                    stmt.setDate(3, from);
                }
                try (ResultSet actual = viaFlight.executeQuery(); ResultSet expected = direct.executeQuery()) {
                    List<String> actualRows = rows(actual);
                    assertThat(actualRows).isEqualTo(rows(expected));
                    assertThat(actualRows.getFirst()).doesNotStartWith("0|");     // the filter matches rows
                }
            }
        }
    }

    @Test
    void testUpdatesAndBatchInsertsGoThroughFlightSql() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url("arrow"));
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE jdbc_orders (id INTEGER, ticker VARCHAR, amount DECIMAL(12, 2))");

            try (PreparedStatement insert = conn.prepareStatement("INSERT INTO jdbc_orders VALUES (?, ?, ?)")) {
                for (int i = 1; i <= 3; i++) {
                    insert.setInt(1, i);
                    insert.setString(2, TestTrades.TICKERS.get(i));
                    insert.setBigDecimal(3, new BigDecimal("10.50").multiply(BigDecimal.valueOf(i)));
                    insert.addBatch();
                }
                // All three parameter rows travel in one DoPut and run in one DuckDB transaction. The driver
                // reports the single DoPut's total, not one count per row as most JDBC drivers do.
                assertThat(insert.executeBatch()).containsExactly(3);
            }
            assertThat(stmt.executeUpdate("UPDATE jdbc_orders SET amount = amount * 2 WHERE id >= 2")).isEqualTo(2);

            try (ResultSet rs = stmt.executeQuery("SELECT id, ticker, amount FROM jdbc_orders ORDER BY id")) {
                assertThat(rows(rs)).containsExactly("1|AMZN|10.50", "2|GOOG|42.00", "3|MSFT|63.00");
            }

            DatabaseMetaData meta = conn.getMetaData();
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            assertThat(tables).contains("trades", "jdbc_orders");

            stmt.executeUpdate("DROP TABLE jdbc_orders");
        }
    }

    @Test
    void testPreparedStatementsAreClosedOnTheServer() throws SQLException {
        int before = producer.openPreparedStatements();
        try (Connection conn = DriverManager.getConnection(url("arrow"))) {
            for (int i = 0; i < 3; i++) {
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    rs.next();
                }
            }
        }
        assertThat(producer.openPreparedStatements()).isEqualTo(before);
    }

    @Test
    void testWrongPasswordAndInvalidSqlAreReported() {
        assertThatThrownBy(() -> DriverManager.getConnection(url("wrong")).close())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNAUTHENTICATED");
        assertThatThrownBy(() -> {
            try (Connection conn = DriverManager.getConnection(url("arrow")); Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM missing_table");
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("missing_table");
    }

    private List<String> direct(String sql) throws SQLException {
        try (Connection conn = duckDB.openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rows(rs);
        }
    }

    /** Rows as "a|b|c" strings, normalizing numbers so DuckDB-direct and Flight SQL results compare equal. */
    private static List<String> rows(ResultSet rs) throws SQLException {
        List<String> rows = new ArrayList<>();
        int columns = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            List<String> values = new ArrayList<>();
            for (int i = 1; i <= columns; i++) {
                Object value = rs.getObject(i);
                values.add(switch (value) {
                    case BigDecimal decimal -> decimal.scale() > 0 ? decimal.toPlainString() : decimal.toBigInteger().toString();
                    case java.math.BigInteger big -> big.toString();
                    case Date date -> date.toLocalDate().toString();
                    case LocalDate date -> date.toString();
                    case null -> "null";
                    default -> value.toString();
                });
            }
            rows.add(String.join("|", values));
        }
        return rows;
    }
}
