package com.rixon.learn.spring.data.arrow.flight;

import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.NoOpFlightProducer;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Arrow Flight service over the DuckDB trades table and client-uploaded datasets.
 * <ul>
 *   <li>{@code path("trades")}: the DuckDB table, split into one endpoint (ticket) per ticker so clients can
 *       fetch the parts in parallel</li>
 *   <li>{@code command(<sql>)}: any SQL query against DuckDB, one endpoint</li>
 *   <li>{@code path(<name>)} after a DoPut: a dataset uploaded by a client, held as Arrow record batches</li>
 *   <li>DoExchange: send trades, receive {@code trade_id, notional} batches computed on the server</li>
 *   <li>Actions: {@code dataset-stats} and {@code drop-dataset}</li>
 * </ul>
 * Each call gets its own {@link VectorSchemaRoot} (and DuckDB connection), so concurrent calls never share state.
 */
@Slf4j
@Component
public class TradeFlightProducer extends NoOpFlightProducer implements AutoCloseable {

    public static final String TRADES = "trades";
    public static final Schema NOTIONAL_SCHEMA = new Schema(List.of(
            Field.notNullable("trade_id", ArrowType.Utf8.INSTANCE),
            Field.notNullable("notional", new ArrowType.Decimal(18, 2, 128))));

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TICKER = Pattern.compile("[A-Z]{1,10}");

    private final DuckDbArrowService duckDB;
    private final BufferAllocator allocator;
    private final Map<String, Dataset> datasets = new ConcurrentHashMap<>();

    public TradeFlightProducer(DuckDbArrowService duckDB, BufferAllocator rootAllocator) {
        this.duckDB = duckDB;
        this.allocator = rootAllocator.newChildAllocator("flight", 0, Long.MAX_VALUE);
    }

    public BufferAllocator allocator() {
        return allocator;
    }

    // ---------------------------------------------------------------- discovery

    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener) {
        try {
            listener.onNext(tradesInfo(FlightDescriptor.path(TRADES)));
            datasets.forEach((name, dataset) -> listener.onNext(datasetInfo(FlightDescriptor.path(name), name, dataset)));
            listener.onCompleted();
        } catch (SQLException e) {
            listener.onError(CallStatus.INTERNAL.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor) {
        try {
            if (descriptor.isCommand()) {
                String sql = new String(descriptor.getCommand(), StandardCharsets.UTF_8);
                return new FlightInfo(schemaOf(sql), descriptor,
                        List.of(new FlightEndpoint(ticket("sql", sql))), -1, -1);
            }
            String name = pathName(descriptor);
            if (TRADES.equals(name)) {
                return tradesInfo(descriptor);
            }
            Dataset dataset = datasets.get(name);
            if (dataset == null) {
                throw CallStatus.NOT_FOUND.withDescription("Unknown flight: " + name).toRuntimeException();
            }
            return datasetInfo(descriptor, name, dataset);
        } catch (SQLException e) {
            throw CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException();
        }
    }

    /** One endpoint per ticker; totalRecords is the exact row count. */
    private FlightInfo tradesInfo(FlightDescriptor descriptor) throws SQLException {
        List<FlightEndpoint> endpoints = new ArrayList<>();
        long total = 0;
        try (Connection conn = duckDB.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ticker, count(*) FROM trades GROUP BY ticker ORDER BY ticker")) {
            while (rs.next()) {
                endpoints.add(new FlightEndpoint(ticket("trades", rs.getString(1))));
                total += rs.getLong(2);
            }
        }
        return new FlightInfo(schemaOf("SELECT * FROM trades"), descriptor, endpoints, -1, total);
    }

    private FlightInfo datasetInfo(FlightDescriptor descriptor, String name, Dataset dataset) {
        return new FlightInfo(dataset.schema(), descriptor,
                List.of(new FlightEndpoint(ticket("dataset", name))), dataset.bytes(), dataset.rows());
    }

    // ---------------------------------------------------------------- DoGet

    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener) {
        String[] parts = new String(ticket.getBytes(), StandardCharsets.UTF_8).split(":", 2);
        try {
            switch (parts[0]) {
                case "trades" -> {
                    if (!TICKER.matcher(parts[1]).matches()) {
                        throw CallStatus.INVALID_ARGUMENT.withDescription("Invalid ticker").toRuntimeException();
                    }
                    streamQuery("SELECT * FROM trades WHERE ticker = ? ORDER BY trade_id", List.of(parts[1]), listener);
                }
                case "sql" -> streamQuery(parts[1], List.of(), listener);
                case "dataset" -> streamDataset(parts[1], listener);
                default -> throw CallStatus.INVALID_ARGUMENT.withDescription("Unknown ticket").toRuntimeException();
            }
        } catch (FlightRuntimeException e) {
            listener.error(e);
        } catch (Exception e) {
            listener.error(CallStatus.INTERNAL.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    private void streamQuery(String sql, List<Object> parameters, ServerStreamListener listener) throws Exception {
        try (DuckDbArrowService.ArrowQuery query = duckDB.query(sql, parameters, allocator)) {
            listener.start(query.root());
            while (query.reader().loadNextBatch()) {
                listener.putNext();
            }
            listener.completed();
        }
    }

    private void streamDataset(String name, ServerStreamListener listener) {
        Dataset dataset = datasets.get(name);
        if (dataset == null) {
            throw CallStatus.NOT_FOUND.withDescription("Unknown dataset: " + name).toRuntimeException();
        }
        try (VectorSchemaRoot root = VectorSchemaRoot.create(dataset.schema(), allocator)) {
            VectorLoader loader = new VectorLoader(root);
            listener.start(root);
            for (ArrowRecordBatch batch : dataset.batches()) {
                loader.load(batch);
                listener.putNext();
            }
            listener.completed();
        }
    }

    // ---------------------------------------------------------------- DoPut

    /** Stores the uploaded stream under the descriptor's path name, replacing any previous upload. */
    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream) {
        return () -> {
            String name = pathName(flightStream.getDescriptor());
            if (TRADES.equals(name)) {
                ackStream.onError(CallStatus.INVALID_ARGUMENT.withDescription("'trades' is read-only").toRuntimeException());
                return;
            }
            List<ArrowRecordBatch> batches = new ArrayList<>();
            long rows = 0;
            try {
                VectorSchemaRoot root = flightStream.getRoot();
                while (flightStream.next()) {
                    // The stream reuses one root; unload each batch (retaining its buffers) before the next arrives
                    batches.add(new VectorUnloader(root).getRecordBatch());
                    rows += root.getRowCount();
                }
                Dataset previous = datasets.put(name, new Dataset(root.getSchema(), List.copyOf(batches), rows));
                if (previous != null) {
                    previous.close();
                }
                log.info("Stored dataset {} ({} rows in {} batches)", name, rows, batches.size());
                ackStream.onCompleted();
            } catch (Exception e) {
                batches.forEach(ArrowRecordBatch::close);
                ackStream.onError(CallStatus.INTERNAL.withDescription(e.getMessage()).withCause(e).toRuntimeException());
            }
        };
    }

    // ---------------------------------------------------------------- DoExchange

    /** Reads trade batches and answers each one with a batch of {@code trade_id, notional = price * quantity}. */
    @Override
    public void doExchange(CallContext context, FlightStream reader, ServerStreamListener writer) {
        try (VectorSchemaRoot out = VectorSchemaRoot.create(NOTIONAL_SCHEMA, allocator)) {
            writer.start(out);
            while (reader.next()) {
                VectorSchemaRoot in = reader.getRoot();
                VarCharVector tradeIds = (VarCharVector) in.getVector("trade_id");
                DecimalVector prices = (DecimalVector) in.getVector("price");
                BigIntVector quantities = (BigIntVector) in.getVector("quantity");
                VarCharVector outIds = (VarCharVector) out.getVector("trade_id");
                DecimalVector notional = (DecimalVector) out.getVector("notional");
                out.allocateNew();
                for (int i = 0; i < in.getRowCount(); i++) {
                    outIds.setSafe(i, tradeIds.get(i));
                    notional.setSafe(i, prices.getObject(i).multiply(BigDecimal.valueOf(quantities.get(i))));
                }
                out.setRowCount(in.getRowCount());
                writer.putNext();
            }
            writer.completed();
        } catch (Exception e) {
            writer.error(CallStatus.INTERNAL.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener) {
        listener.onNext(new ActionType("dataset-stats", "Rows, batches and bytes of an uploaded dataset (body: name)"));
        listener.onNext(new ActionType("drop-dataset", "Remove an uploaded dataset and free its memory (body: name)"));
        listener.onCompleted();
    }

    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener) {
        String name = new String(action.getBody(), StandardCharsets.UTF_8);
        switch (action.getType()) {
            case "dataset-stats" -> {
                Dataset dataset = requireDataset(name);
                listener.onNext(new Result("{\"rows\":%d,\"batches\":%d,\"bytes\":%d}"
                        .formatted(dataset.rows(), dataset.batches().size(), dataset.bytes()).getBytes(StandardCharsets.UTF_8)));
                listener.onCompleted();
            }
            case "drop-dataset" -> {
                Dataset dataset = datasets.remove(requireName(name));
                if (dataset == null) {
                    throw CallStatus.NOT_FOUND.withDescription("Unknown dataset: " + name).toRuntimeException();
                }
                dataset.close();
                listener.onNext(new Result(("dropped " + name).getBytes(StandardCharsets.UTF_8)));
                listener.onCompleted();
            }
            default -> throw CallStatus.UNIMPLEMENTED.withDescription("Unknown action: " + action.getType()).toRuntimeException();
        }
    }

    // ---------------------------------------------------------------- helpers

    private Schema schemaOf(String sql) throws SQLException {
        try (DuckDbArrowService.ArrowQuery query = duckDB.query("SELECT * FROM (" + sql + ") q LIMIT 0", allocator)) {
            return query.root().getSchema();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    private Dataset requireDataset(String name) {
        Dataset dataset = datasets.get(requireName(name));
        if (dataset == null) {
            throw CallStatus.NOT_FOUND.withDescription("Unknown dataset: " + name).toRuntimeException();
        }
        return dataset;
    }

    private static String pathName(FlightDescriptor descriptor) {
        if (descriptor.isCommand() || descriptor.getPath().size() != 1) {
            throw CallStatus.INVALID_ARGUMENT.withDescription("Expected a single-element path descriptor").toRuntimeException();
        }
        return requireName(descriptor.getPath().getFirst());
    }

    private static String requireName(String name) {
        if (!NAME.matcher(name).matches()) {
            throw CallStatus.INVALID_ARGUMENT.withDescription("Invalid name: " + name).toRuntimeException();
        }
        return name;
    }

    private static Ticket ticket(String kind, String value) {
        return new Ticket((kind + ":" + value).getBytes(StandardCharsets.UTF_8));
    }

    /** Frees every uploaded dataset, then the producer's allocator (which fails if anything leaked). */
    @Override
    public void close() {
        datasets.values().forEach(Dataset::close);
        datasets.clear();
        allocator.close();
    }

    /** An uploaded dataset: record batches whose buffers are retained until the dataset is dropped. */
    private record Dataset(Schema schema, List<ArrowRecordBatch> batches, long rows) implements AutoCloseable {

        long bytes() {
            return batches.stream().mapToLong(ArrowRecordBatch::computeBodyLength).sum();
        }

        @Override
        public void close() {
            batches.forEach(ArrowRecordBatch::close);
        }
    }
}
