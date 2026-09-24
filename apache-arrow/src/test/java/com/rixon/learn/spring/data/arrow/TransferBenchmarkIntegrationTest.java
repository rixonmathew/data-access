package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the whole trades table four ways and checks they return identical data. Timings are logged for
 * comparison only; they depend on the machine, so nothing is asserted about them.
 */
@ArrowIntegrationTest
class TransferBenchmarkIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TransferBenchmarkIntegrationTest.class);
    private static final String SQL = "SELECT * FROM trades";

    @Autowired
    private DuckDbArrowService duckDB;

    @Autowired
    @Qualifier("tradeFlightServer")
    private FlightServer flightServer;

    @Autowired
    @Qualifier("flightSqlServer")
    private FlightServer flightSqlServer;

    @Autowired
    private BufferAllocator rootAllocator;

    record Transfer(String method, long rows, long quantity, BigDecimal price, long millis) {
        boolean sameData(Transfer other) {
            return rows == other.rows && quantity == other.quantity && price.compareTo(other.price) == 0;
        }
    }

    @Test
    void testAllTransferPathsReturnIdenticalData() throws Exception {
        List<Transfer> results = new ArrayList<>();
        for (int run = 0; run < 2; run++) {                       // first run warms up JIT and connections
            results.clear();
            results.add(timed("JDBC rows (DuckDB driver)", this::jdbcRows));
            results.add(timed("Arrow export (DuckDB C Data Interface)", this::arrowExport));
            results.add(timed("Arrow Flight DoGet (5 endpoints in parallel)", this::flightParallel));
            results.add(timed("Flight SQL JDBC driver", this::flightSqlJdbc));
        }

        Transfer reference = results.getFirst();
        assertThat(reference.rows()).isEqualTo(ArrowIntegrationTest.SAMPLE_ROWS);
        assertThat(results).allSatisfy(r -> assertThat(r.sameData(reference)).as(r.method()).isTrue());

        log.info("Reading {} trades:", reference.rows());
        results.forEach(r -> log.info("  {} ms  {}", String.format("%6d", r.millis()), r.method()));
    }

    private Transfer timed(String method, Reader reader) throws Exception {
        long start = System.nanoTime();
        Transfer t = reader.read();
        return new Transfer(method, t.rows(), t.quantity(), t.price(), (System.nanoTime() - start) / 1_000_000);
    }

    @FunctionalInterface
    private interface Reader {
        Transfer read() throws Exception;
    }

    /** Row-at-a-time JDBC: every value goes through a getter call. */
    private Transfer jdbcRows() throws SQLException {
        long rows = 0;
        long quantity = 0;
        BigDecimal price = BigDecimal.ZERO;
        try (Connection conn = duckDB.openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(SQL)) {
            while (rs.next()) {
                rs.getString("trade_id");
                rs.getString("ticker");
                price = price.add(rs.getBigDecimal("price"));
                quantity += rs.getLong("quantity");
                rs.getDate("trade_date");
                rs.getString("venue");
                rows++;
            }
        }
        return new Transfer(null, rows, quantity, price, 0);
    }

    /** DuckDB writes result batches straight into Arrow vectors in this JVM. */
    private Transfer arrowExport() throws Exception {
        Totals totals = new Totals();
        try (BufferAllocator allocator = rootAllocator.newChildAllocator("bench-export", 0, Long.MAX_VALUE);
             DuckDbArrowService.ArrowQuery query = duckDB.query(SQL, allocator)) {
            while (query.reader().loadNextBatch()) {
                totals.add(query.root());
            }
        }
        return totals.toTransfer();
    }

    /** Flight: the table's 5 endpoints streamed over gRPC concurrently. */
    private Transfer flightParallel() throws Exception {
        try (BufferAllocator allocator = rootAllocator.newChildAllocator("bench-flight", 0, Long.MAX_VALUE);
             FlightClient client = FlightClient.builder(allocator, Location.forGrpcInsecure("localhost", flightServer.getPort())).build()) {
            CallOption auth = client.authenticateBasicToken("arrow", "arrow").orElseThrow();
            FlightInfo info = client.getInfo(FlightDescriptor.path("trades"), auth);
            ExecutorService pool = Executors.newFixedThreadPool(info.getEndpoints().size());
            try {
                List<Future<Totals>> parts = new ArrayList<>();
                for (FlightEndpoint endpoint : info.getEndpoints()) {
                    parts.add(pool.submit(() -> {
                        Totals part = new Totals();
                        try (FlightStream stream = client.getStream(endpoint.getTicket(), auth)) {
                            while (stream.next()) {
                                part.add(stream.getRoot());
                            }
                        }
                        return part;
                    }));
                }
                Totals totals = new Totals();
                for (Future<Totals> part : parts) {
                    totals.merge(part.get());
                }
                return totals.toTransfer();
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** Standard JDBC API, but the driver receives Arrow batches over Flight SQL underneath. */
    private Transfer flightSqlJdbc() throws SQLException {
        String url = "jdbc:arrow-flight-sql://localhost:%d/?useEncryption=false&user=arrow&password=arrow"
                .formatted(flightSqlServer.getPort());
        long rows = 0;
        long quantity = 0;
        BigDecimal price = BigDecimal.ZERO;
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(SQL)) {
            while (rs.next()) {
                rs.getString("trade_id");
                rs.getString("ticker");
                price = price.add(rs.getBigDecimal("price"));
                quantity += rs.getLong("quantity");
                rs.getDate("trade_date");
                rs.getString("venue");
                rows++;
            }
        }
        return new Transfer(null, rows, quantity, price, 0);
    }

    private static final class Totals {
        long rows;
        long quantity;
        BigDecimal price = BigDecimal.ZERO;

        void add(VectorSchemaRoot root) {
            BigIntVector quantities = (BigIntVector) root.getVector("quantity");
            DecimalVector prices = (DecimalVector) root.getVector("price");
            for (int i = 0; i < root.getRowCount(); i++) {
                quantity += quantities.get(i);
                price = price.add(prices.getObject(i));
            }
            rows += root.getRowCount();
        }

        void merge(Totals other) {
            rows += other.rows;
            quantity += other.quantity;
            price = price.add(other.price);
        }

        Transfer toTransfer() {
            return new Transfer(null, rows, quantity, price, 0);
        }
    }
}
