package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.flight.TradeFlightProducer;
import com.rixon.learn.spring.data.arrow.model.Trade;
import com.rixon.learn.spring.data.arrow.service.ArrowColumnarService;
import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.FlightStatusCode;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.grpc.CredentialCallOption;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Arrow Flight RPCs against {@link TradeFlightProducer}, over real gRPC on localhost. */
@ArrowIntegrationTest
class ArrowFlightIntegrationTest {

    @Autowired
    @Qualifier("tradeFlightServer")
    private FlightServer server;

    @Autowired
    private TradeFlightProducer producer;

    @Autowired
    private DuckDbArrowService duckDB;

    @Autowired
    private ArrowColumnarService columnar;

    @Autowired
    private BufferAllocator rootAllocator;

    private BufferAllocator allocator;
    private FlightClient client;
    private CallOption auth;

    @BeforeEach
    void setUp() {
        allocator = rootAllocator.newChildAllocator("flight-test", 0, Long.MAX_VALUE);
        client = FlightClient.builder(allocator, Location.forGrpcInsecure("localhost", server.getPort())).build();
        // Basic credentials on the handshake; the server answers with a bearer token used for every later call
        CredentialCallOption token = client.authenticateBasicToken("arrow", "arrow").orElseThrow();
        auth = token;
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        assertThat(allocator.getAllocatedMemory()).as("client-side Arrow memory leaked").isZero();
        allocator.close();
    }

    @Test
    void testCallsWithoutValidCredentialsAreRejected() throws Exception {
        try (FlightClient anonymous = FlightClient.builder(allocator, Location.forGrpcInsecure("localhost", server.getPort())).build()) {
            assertThatThrownBy(() -> anonymous.listFlights(Criteria.ALL).forEach(info -> { }))
                    .isInstanceOfSatisfying(FlightRuntimeException.class,
                            e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.UNAUTHENTICATED));
            assertThatThrownBy(() -> anonymous.authenticateBasicToken("arrow", "wrong-password"))
                    .isInstanceOfSatisfying(FlightRuntimeException.class,
                            e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.UNAUTHENTICATED));
        }
    }

    @Test
    void testTradesAreSplitIntoEndpointsFetchedInParallel() throws Exception {
        FlightInfo info = client.getInfo(FlightDescriptor.path("trades"), auth);
        assertThat(info.getRecords()).isEqualTo(ArrowIntegrationTest.SAMPLE_ROWS);
        assertThat(info.getEndpoints()).hasSize(5);                              // one per ticker
        assertThat(info.getSchemaOptional().orElseThrow().getFields()).extracting(f -> f.getName())
                .containsExactly("trade_id", "ticker", "price", "quantity", "trade_date", "venue");

        ExecutorService pool = Executors.newFixedThreadPool(info.getEndpoints().size());
        try {
            List<Future<long[]>> parts = new ArrayList<>();
            for (FlightEndpoint endpoint : info.getEndpoints()) {
                parts.add(pool.submit(() -> readEndpoint(endpoint)));
            }
            long rows = 0;
            long quantity = 0;
            for (Future<long[]> part : parts) {
                rows += part.get()[0];
                quantity += part.get()[1];
            }
            assertThat(rows).isEqualTo(ArrowIntegrationTest.SAMPLE_ROWS);
            assertThat(quantity).isEqualTo(jdbcLong("SELECT sum(quantity) FROM trades"));
        } finally {
            pool.shutdownNow();
        }
    }

    /** Streams one endpoint and returns {rows, sum(quantity)}, checking every row belongs to the endpoint's ticker. */
    private long[] readEndpoint(FlightEndpoint endpoint) throws Exception {
        String ticker = new String(endpoint.getTicket().getBytes(), StandardCharsets.UTF_8).split(":")[1];
        long rows = 0;
        long quantity = 0;
        try (FlightStream stream = client.getStream(endpoint.getTicket(), auth)) {
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                VarCharVector tickers = (VarCharVector) root.getVector("ticker");
                BigIntVector quantities = (BigIntVector) root.getVector("quantity");
                for (int i = 0; i < root.getRowCount(); i++) {
                    assertThat(tickers.getObject(i).toString()).isEqualTo(ticker);
                    quantity += quantities.get(i);
                }
                rows += root.getRowCount();
            }
        }
        return new long[]{rows, quantity};
    }

    @Test
    void testSqlCommandDescriptorRunsQueryInDuckDb() throws Exception {
        FlightInfo info = client.getInfo(FlightDescriptor.command(
                "SELECT ticker, sum(quantity) AS total FROM trades GROUP BY ticker ORDER BY ticker".getBytes(StandardCharsets.UTF_8)), auth);
        List<String> tickers = new ArrayList<>();
        long total = 0;
        try (FlightStream stream = client.getStream(info.getEndpoints().getFirst().getTicket(), auth)) {
            while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                for (int i = 0; i < root.getRowCount(); i++) {
                    tickers.add(root.getVector("ticker").getObject(i).toString());
                    total += ((Number) root.getVector("total").getObject(i)).longValue();
                }
            }
        }
        assertThat(tickers).containsExactlyElementsOf(TestTrades.TICKERS);
        assertThat(total).isEqualTo(jdbcLong("SELECT sum(quantity) FROM trades"));
    }

    @Test
    void testDoPutStoresDatasetThatCanBeListedFetchedInspectedAndDropped() throws Exception {
        List<Trade> trades = TestTrades.generate(7_000);
        FlightDescriptor descriptor = FlightDescriptor.path("uploaded_trades");

        try (VectorSchemaRoot root = VectorSchemaRoot.create(ArrowColumnarService.TRADE_SCHEMA, allocator)) {
            FlightClient.ClientStreamListener writer = client.startPut(descriptor, root, new AsyncPutListener(), auth);
            for (int from = 0; from < trades.size(); from += 3_000) {
                columnar.fill(root, trades.subList(from, Math.min(from + 3_000, trades.size())));
                writer.putNext();
            }
            writer.completed();
            writer.getResult();
        }

        List<FlightInfo> flights = new ArrayList<>();
        client.listFlights(Criteria.ALL, auth).forEach(flights::add);
        assertThat(flights).extracting(i -> i.getDescriptor().getPath().getFirst()).contains("trades", "uploaded_trades");
        assertThat(client.getInfo(descriptor, auth).getRecords()).isEqualTo(7_000);

        List<Trade> fetched = new ArrayList<>();
        try (FlightStream stream = client.getStream(client.getInfo(descriptor, auth).getEndpoints().getFirst().getTicket(), auth)) {
            while (stream.next()) {
                fetched.addAll(columnar.fromVectors(stream.getRoot()));
            }
        }
        assertThat(fetched).isEqualTo(trades);

        List<String> actions = new ArrayList<>();
        client.listActions(auth).forEach(a -> actions.add(a.getType()));
        assertThat(actions).containsExactly("dataset-stats", "drop-dataset");

        String stats = firstResult(client.doAction(new Action("dataset-stats", bytes("uploaded_trades")), auth));
        assertThat(stats).startsWith("{\"rows\":7000,\"batches\":3,\"bytes\":");

        assertThat(firstResult(client.doAction(new Action("drop-dataset", bytes("uploaded_trades")), auth)))
                .isEqualTo("dropped uploaded_trades");
        assertThatThrownBy(() -> client.getInfo(descriptor, auth))
                .isInstanceOfSatisfying(FlightRuntimeException.class,
                        e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.NOT_FOUND));
        awaitServerMemoryReleased();
    }

    @Test
    void testDoExchangeReturnsNotionalPerBatch() throws Exception {
        List<Trade> trades = TestTrades.generate(2_500);
        List<String> ids = new ArrayList<>();
        List<BigDecimal> notionals = new ArrayList<>();

        try (VectorSchemaRoot root = VectorSchemaRoot.create(ArrowColumnarService.TRADE_SCHEMA, allocator);
             FlightClient.ExchangeReaderWriter exchange = client.doExchange(FlightDescriptor.path("notional"), auth)) {
            exchange.getWriter().start(root);
            for (int from = 0; from < trades.size(); from += 1_000) {
                columnar.fill(root, trades.subList(from, Math.min(from + 1_000, trades.size())));
                exchange.getWriter().putNext();
            }
            exchange.getWriter().completed();

            FlightStream results = exchange.getReader();
            int batches = 0;
            while (results.next()) {
                batches++;
                VectorSchemaRoot out = results.getRoot();
                assertThat(out.getSchema()).isEqualTo(TradeFlightProducer.NOTIONAL_SCHEMA);
                for (int i = 0; i < out.getRowCount(); i++) {
                    ids.add(((VarCharVector) out.getVector("trade_id")).getObject(i).toString());
                    notionals.add(((DecimalVector) out.getVector("notional")).getObject(i));
                }
            }
            assertThat(batches).isEqualTo(3);                                   // one answer per request batch
        }

        assertThat(ids).containsExactlyElementsOf(trades.stream().map(Trade::getTradeId).toList());
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            assertThat(notionals.get(i)).isEqualByComparingTo(trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity())));
        }
    }

    @Test
    void testInvalidRequestsReturnFlightStatusCodes() throws Exception {
        assertThatThrownBy(() -> client.getInfo(FlightDescriptor.path("no_such_flight"), auth))
                .isInstanceOfSatisfying(FlightRuntimeException.class,
                        e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.NOT_FOUND));
        assertThatThrownBy(() -> client.getInfo(FlightDescriptor.command(bytes("SELECT * FROM missing_table")), auth))
                .isInstanceOfSatisfying(FlightRuntimeException.class,
                        e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> client.doAction(new Action("reboot", new byte[0]), auth).forEachRemaining(r -> { }))
                .isInstanceOfSatisfying(FlightRuntimeException.class,
                        e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.UNIMPLEMENTED));

        try (VectorSchemaRoot root = VectorSchemaRoot.create(ArrowColumnarService.TRADE_SCHEMA, allocator)) {
            FlightClient.ClientStreamListener writer = client.startPut(FlightDescriptor.path("trades"), root, new AsyncPutListener(), auth);
            writer.completed();
            assertThatThrownBy(writer::getResult)
                    .isInstanceOfSatisfying(FlightRuntimeException.class,
                            e -> assertThat(e.status().code()).isEqualTo(FlightStatusCode.INVALID_ARGUMENT));
        }
    }

    private void awaitServerMemoryReleased() throws InterruptedException {
        // gRPC frees the last server-side message buffers just after the call completes
        for (int i = 0; i < 50 && producer.allocator().getAllocatedMemory() != 0; i++) {
            Thread.sleep(100);
        }
        assertThat(producer.allocator().getAllocatedMemory()).as("server-side Arrow memory after drop").isZero();
    }

    private static String firstResult(java.util.Iterator<Result> results) {
        String first = new String(results.next().getBody(), StandardCharsets.UTF_8);
        results.forEachRemaining(r -> { });
        return first;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private long jdbcLong(String sql) throws SQLException {
        try (Connection conn = duckDB.openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
