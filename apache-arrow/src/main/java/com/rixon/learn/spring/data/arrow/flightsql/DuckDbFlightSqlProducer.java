package com.rixon.learn.spring.data.arrow.flightsql;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.rixon.learn.spring.data.arrow.service.DuckDbArrowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.flight.sql.BasicFlightSqlProducer;
import org.apache.arrow.flight.sql.FlightSqlProducer;
import org.apache.arrow.flight.sql.impl.FlightSql.ActionClosePreparedStatementRequest;
import org.apache.arrow.flight.sql.impl.FlightSql.ActionCreatePreparedStatementRequest;
import org.apache.arrow.flight.sql.impl.FlightSql.ActionCreatePreparedStatementResult;
import org.apache.arrow.flight.sql.impl.FlightSql.CommandGetTables;
import org.apache.arrow.flight.sql.impl.FlightSql.CommandPreparedStatementQuery;
import org.apache.arrow.flight.sql.impl.FlightSql.CommandPreparedStatementUpdate;
import org.apache.arrow.flight.sql.impl.FlightSql.DoPutUpdateResult;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arrow Flight SQL over DuckDB: any Flight SQL client (including the Flight SQL JDBC driver) can run SQL and
 * receive results as Arrow record batches.
 * <p>
 * The JDBC driver runs every statement as a prepared statement: create (returns the result and parameter
 * schemas), optional DoPut to bind parameter values, GetFlightInfo + DoGet for queries or DoPut for updates,
 * then close. Table metadata ({@code DatabaseMetaData.getTables}) is answered from DuckDB's information_schema.
 */
@Slf4j
@Component
public class DuckDbFlightSqlProducer extends BasicFlightSqlProducer implements AutoCloseable {

    private final DuckDbArrowService duckDB;
    private final BufferAllocator allocator;
    private final Map<ByteString, PreparedQuery> prepared = new ConcurrentHashMap<>();

    public DuckDbFlightSqlProducer(DuckDbArrowService duckDB, BufferAllocator rootAllocator) {
        this.duckDB = duckDB;
        this.allocator = rootAllocator.newChildAllocator("flight-sql", 0, Long.MAX_VALUE);
    }

    public BufferAllocator allocator() {
        return allocator;
    }

    public int openPreparedStatements() {
        return prepared.size();
    }

    @Override
    protected <T extends Message> List<FlightEndpoint> determineEndpoints(T command, FlightDescriptor descriptor, Schema schema) {
        return List.of(new FlightEndpoint(new Ticket(Any.pack(command).toByteArray())));
    }

    // ---------------------------------------------------------------- prepared statements

    @Override
    public void createPreparedStatement(ActionCreatePreparedStatementRequest request, CallContext context,
                                        StreamListener<Result> listener) {
        String sql = request.getQuery();
        try (Connection conn = duckDB.openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            Schema parameterSchema = parameterSchema(stmt.getParameterMetaData());
            boolean returnsRows = returnsRows(sql);
            Schema datasetSchema = returnsRows ? resultSchema(sql, parameterSchema.getFields().size()) : new Schema(List.of());

            ByteString handle = ByteString.copyFromUtf8(UUID.randomUUID().toString());
            prepared.put(handle, new PreparedQuery(sql, returnsRows, datasetSchema, new ArrayList<>(), new AtomicBoolean()));
            ActionCreatePreparedStatementResult result = ActionCreatePreparedStatementResult.newBuilder()
                    .setPreparedStatementHandle(handle)
                    .setDatasetSchema(ByteString.copyFrom(datasetSchema.serializeAsMessage()))
                    .setParameterSchema(ByteString.copyFrom(parameterSchema.serializeAsMessage()))
                    .build();
            listener.onNext(new Result(Any.pack(result).toByteArray()));
            listener.onCompleted();
        } catch (SQLException e) {
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    /** Binds parameter values for a prepared query (the first row of the uploaded batch). */
    @Override
    public Runnable acceptPutPreparedStatementQuery(CommandPreparedStatementQuery command, CallContext context,
                                                    FlightStream flightStream, StreamListener<PutResult> ackStream) {
        return () -> {
            try {
                PreparedQuery query = require(command.getPreparedStatementHandle());
                List<List<Object>> rows = readParameterRows(flightStream);
                synchronized (query) {
                    query.boundRows().clear();
                    query.boundRows().addAll(rows);
                }
                ackStream.onCompleted();
            } catch (FlightRuntimeException e) {
                ackStream.onError(e);
            }
        };
    }

    @Override
    public FlightInfo getFlightInfoPreparedStatement(CommandPreparedStatementQuery command, CallContext context,
                                                     FlightDescriptor descriptor) {
        PreparedQuery query = require(command.getPreparedStatementHandle());
        return new FlightInfo(query.datasetSchema(), descriptor, determineEndpoints(command, descriptor, query.datasetSchema()), -1, -1);
    }

    @Override
    public void getStreamPreparedStatement(CommandPreparedStatementQuery command, CallContext context,
                                           ServerStreamListener listener) {
        try {
            PreparedQuery query = require(command.getPreparedStatementHandle());
            List<Object> parameters;
            synchronized (query) {
                parameters = query.boundRows().isEmpty() ? List.of() : query.boundRows().getFirst();
            }
            if (!query.returnsRows()) {
                // The JDBC driver follows executeUpdate's DoPut with GetFlightInfo + DoGet on the same statement:
                // answer with an empty result instead of running the DDL/DML a second time
                if (!query.executed().getAndSet(false)) {
                    executeUpdate(query.sql(), parameters.isEmpty() ? List.of() : List.of(parameters));
                }
                try (VectorSchemaRoot empty = VectorSchemaRoot.create(query.datasetSchema(), allocator)) {
                    listener.start(empty);
                    listener.completed();
                }
                return;
            }
            try (DuckDbArrowService.ArrowQuery result = duckDB.query(query.sql(), parameters, allocator)) {
                listener.start(result.root());
                while (result.reader().loadNextBatch()) {
                    listener.putNext();
                }
                listener.completed();
            }
        } catch (FlightRuntimeException e) {
            listener.error(e);
        } catch (Exception e) {
            listener.error(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    /**
     * Executes a prepared INSERT/UPDATE/DELETE once per uploaded parameter row (JDBC {@code executeBatch}
     * sends all rows in one DoPut), or once without parameters, in a single DuckDB transaction.
     */
    @Override
    public Runnable acceptPutPreparedStatementUpdate(CommandPreparedStatementUpdate command, CallContext context,
                                                     FlightStream flightStream, StreamListener<PutResult> ackStream) {
        return () -> {
            try {
                PreparedQuery query = require(command.getPreparedStatementHandle());
                List<List<Object>> rows = readParameterRows(flightStream);
                long updated = executeUpdate(query.sql(), rows);
                query.executed().set(true);
                byte[] result = DoPutUpdateResult.newBuilder().setRecordCount(updated).build().toByteArray();
                try (ArrowBuf buffer = allocator.buffer(result.length)) {
                    buffer.writeBytes(result);
                    ackStream.onNext(PutResult.metadata(buffer));
                }
                ackStream.onCompleted();
            } catch (FlightRuntimeException e) {
                ackStream.onError(e);
            } catch (SQLException e) {
                ackStream.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
            }
        };
    }

    /** Runs the statement once per parameter row (or once without parameters) in one DuckDB transaction. */
    private long executeUpdate(String sql, List<List<Object>> rows) throws SQLException {
        long updated = 0;
        try (Connection conn = duckDB.openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            try {
                for (List<Object> row : rows.isEmpty() ? List.<List<Object>>of(List.of()) : rows) {
                    bind(stmt, row);
                    // DuckDB reports -1 for DDL; JDBC (and the Flight SQL driver) expect 0
                    updated += Math.max(stmt.executeUpdate(), 0);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        return updated;
    }

    @Override
    public void closePreparedStatement(ActionClosePreparedStatementRequest request, CallContext context,
                                       StreamListener<Result> listener) {
        prepared.remove(request.getPreparedStatementHandle());
        listener.onCompleted();
    }

    // ---------------------------------------------------------------- metadata

    /** {@code DatabaseMetaData.getTables}: DuckDB tables and views, filtered by the client's LIKE patterns. */
    @Override
    public void getStreamTables(CommandGetTables command, CallContext context, ServerStreamListener listener) {
        Schema schema = command.getIncludeSchema()
                ? FlightSqlProducer.Schemas.GET_TABLES_SCHEMA
                : FlightSqlProducer.Schemas.GET_TABLES_SCHEMA_NO_SCHEMA;
        String sql = """
                SELECT table_catalog, table_schema, table_name,
                       CASE table_type WHEN 'BASE TABLE' THEN 'TABLE' ELSE table_type END AS table_type
                FROM information_schema.tables
                WHERE table_schema LIKE ? AND table_name LIKE ?
                ORDER BY table_catalog, table_schema, table_name""";
        try (Connection conn = duckDB.openConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            stmt.setString(1, command.hasDbSchemaFilterPattern() ? command.getDbSchemaFilterPattern() : "%");
            stmt.setString(2, command.hasTableNameFilterPattern() ? command.getTableNameFilterPattern() : "%");
            List<String> types = command.getTableTypesList();
            root.allocateNew();
            int row = 0;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (!types.isEmpty() && !types.contains(rs.getString(4))) {
                        continue;
                    }
                    for (int col = 0; col < 4; col++) {
                        ((VarCharVector) root.getVector(col)).setSafe(row, new Text(rs.getString(col + 1)));
                    }
                    if (command.getIncludeSchema()) {
                        Schema tableSchema = resultSchema("SELECT * FROM \"" + rs.getString(2) + "\".\"" + rs.getString(3) + "\"", 0);
                        ((VarBinaryVector) root.getVector("table_schema")).setSafe(row, tableSchema.serializeAsMessage());
                    }
                    row++;
                }
            }
            root.setRowCount(row);
            listener.start(root);
            listener.putNext();
            listener.completed();
        } catch (SQLException e) {
            listener.error(CallStatus.INTERNAL.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    // ---------------------------------------------------------------- helpers

    private PreparedQuery require(ByteString handle) {
        PreparedQuery query = prepared.get(handle);
        if (query == null) {
            throw CallStatus.NOT_FOUND.withDescription("Unknown prepared statement").toRuntimeException();
        }
        return query;
    }

    /** Result schema without running the query: wrap it in LIMIT 0 with every parameter bound to NULL. */
    private Schema resultSchema(String sql, int parameterCount) throws SQLException {
        List<Object> nulls = new ArrayList<>();
        for (int i = 0; i < parameterCount; i++) {
            nulls.add(null);
        }
        try (DuckDbArrowService.ArrowQuery query = duckDB.query("SELECT * FROM (" + sql + ") q LIMIT 0", nulls, allocator)) {
            return query.root().getSchema();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /** Arrow types for the statement's parameters, from DuckDB's inferred parameter types. */
    private static Schema parameterSchema(ParameterMetaData metaData) throws SQLException {
        List<Field> fields = new ArrayList<>();
        for (int i = 1; i <= metaData.getParameterCount(); i++) {
            ArrowType type = switch (metaData.getParameterType(i)) {
                case Types.BIGINT -> new ArrowType.Int(64, true);
                case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> new ArrowType.Int(32, true);
                case Types.DOUBLE, Types.FLOAT, Types.REAL -> new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
                case Types.DECIMAL, Types.NUMERIC -> new ArrowType.Decimal(
                        Math.max(metaData.getPrecision(i), 1), metaData.getScale(i), 128);
                case Types.DATE -> new ArrowType.Date(DateUnit.DAY);
                case Types.BOOLEAN, Types.BIT -> ArrowType.Bool.INSTANCE;
                default -> ArrowType.Utf8.INSTANCE;
            };
            fields.add(Field.nullable("parameter_" + i, type));
        }
        return new Schema(fields);
    }

    /** Every row of the uploaded parameter batches as Java values DuckDB's JDBC driver accepts. */
    private static List<List<Object>> readParameterRows(FlightStream stream) {
        List<List<Object>> rows = new ArrayList<>();
        while (stream.next()) {
            VectorSchemaRoot root = stream.getRoot();
            for (int row = 0; row < root.getRowCount(); row++) {
                List<Object> values = new ArrayList<>();
                for (FieldVector vector : root.getFieldVectors()) {
                    values.add(toJava(vector, row));
                }
                rows.add(values);
            }
        }
        return rows;
    }

    private static Object toJava(FieldVector vector, int row) {
        if (vector.isNull(row)) {
            return null;
        }
        if (vector instanceof DateDayVector days) {
            return LocalDate.ofEpochDay(days.get(row));
        }
        Object value = vector.getObject(row);
        return value instanceof Text text ? text.toString() : value;
    }

    private static void bind(PreparedStatement stmt, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            stmt.setObject(i + 1, values.get(i));
        }
    }

    private static boolean returnsRows(String sql) {
        String keyword = sql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return switch (keyword) {
            case "SELECT", "WITH", "FROM", "VALUES", "TABLE" -> true;
            default -> false;
        };
    }

    @Override
    public void close() {
        prepared.clear();
        allocator.close();
    }

    /**
     * @param executed set when a DoPut update has just run a non-query statement; the next DoGet consumes it
     *                 instead of running the statement again
     */
    private record PreparedQuery(String sql, boolean returnsRows, Schema datasetSchema, List<List<Object>> boundRows,
                                 AtomicBoolean executed) {
    }
}
