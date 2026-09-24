package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.config.DuckLakeProperties;
import com.rixon.learn.spring.data.ducklake.model.CompactionResult;
import com.rixon.learn.spring.data.ducklake.model.DuckLakeDataFile;
import com.rixon.learn.spring.data.ducklake.model.DuckLakeSnapshot;
import com.rixon.learn.spring.data.ducklake.model.Trade;
import com.rixon.learn.spring.data.ducklake.model.TradeChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.rixon.learn.spring.data.ducklake.service.DuckDBService.identifier;
import static com.rixon.learn.spring.data.ducklake.service.DuckDBService.literal;

/**
 * DuckLake table lifecycle on a trades table: ACID commits that each create a snapshot,
 * time travel, the change feed, schema and partition evolution, data inlining, and
 * maintenance (compaction, snapshot expiry, file cleanup).
 * <p>
 * The catalog is attached once at startup under {@link DuckLakeProperties#getCatalogAlias()} and every
 * statement uses fully qualified names, so the default in-memory DuckDB database stays separate.
 */
@Slf4j
@Service
public class DuckLakeService {

    private static final Pattern PARTITION_EXPRESSION =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\([A-Za-z_][A-Za-z0-9_]*\\))?");

    private final DuckDBService duckDB;
    private final String catalog;

    public DuckLakeService(DuckDBService duckDB, DuckLakeProperties properties) throws SQLException {
        this.duckDB = duckDB;
        this.catalog = identifier(properties.getCatalogAlias());
        attachCatalog(properties);
    }

    private void attachCatalog(DuckLakeProperties properties) throws SQLException {
        DuckLakeProperties.Catalog cfg = properties.getCatalog();
        String metadata;
        try (Connection conn = duckDB.openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL ducklake");
            stmt.execute("LOAD ducklake");
            verifyExtensionVersion(stmt, properties.getExpectedExtensionVersion());
            if (cfg.getType() == DuckLakeProperties.CatalogType.POSTGRES) {
                stmt.execute("INSTALL postgres");
                stmt.execute("LOAD postgres");
                // Unnamed postgres secret: picked up by the ATTACH below, so no password is embedded in it
                stmt.execute("""
                        CREATE OR REPLACE SECRET (
                            TYPE postgres,
                            HOST %s,
                            PORT %d,
                            DATABASE %s,
                            USER %s,
                            PASSWORD %s)
                        """.formatted(literal(cfg.getHost()), cfg.getPort(), literal(cfg.getDatabase()),
                        literal(cfg.getUsername()), literal(cfg.getPassword())));
                metadata = "postgres:dbname=" + cfg.getDatabase();
            } else {
                File parent = new File(cfg.getPath()).getAbsoluteFile().getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                metadata = cfg.getPath();
            }
            if (!properties.getDataPath().startsWith("s3://")) {
                new File(properties.getDataPath()).mkdirs();
            }
            String options = "DATA_PATH " + literal(properties.getDataPath());
            if (properties.getDataInliningRowLimit() != null) {
                options += ", DATA_INLINING_ROW_LIMIT " + properties.getDataInliningRowLimit();
            }
            stmt.execute("ATTACH IF NOT EXISTS %s AS %s (%s)"
                    .formatted(literal("ducklake:" + metadata), catalog, options));
        }
        log.info("DuckLake catalog '{}' attached ({} metadata, data path {})",
                catalog, cfg.getType(), properties.getDataPath());
    }

    private static void verifyExtensionVersion(Statement stmt, String expected) throws SQLException {
        String duckdbVersion;
        String ducklakeVersion;
        try (ResultSet rs = stmt.executeQuery("""
                SELECT version() AS duckdb_version, extension_version
                FROM duckdb_extensions() WHERE extension_name = 'ducklake'""")) {
            if (!rs.next()) {
                throw new IllegalStateException("ducklake extension is not loaded");
            }
            duckdbVersion = rs.getString("duckdb_version");
            ducklakeVersion = rs.getString("extension_version");
        }
        log.info("DuckDB {} with ducklake extension build {}", duckdbVersion, ducklakeVersion);
        if (StringUtils.hasText(expected) && !expected.equals(ducklakeVersion)) {
            throw new IllegalStateException(("ducklake extension build %s is loaded but this module was tested with %s. "
                    + "Re-run the tests and update ducklake.expected-extension-version, or set it to an empty value "
                    + "to skip this check.").formatted(ducklakeVersion, expected));
        }
    }

    public String getCatalog() {
        return catalog;
    }

    /**
     * Creates the trades table if it does not exist, partitioned by the given expressions
     * (e.g. {@code ticker}, {@code year(trade_date)}).
     *
     * @return true if the table was created, false if it already existed
     */
    public boolean createTradesTable(String table, List<String> partitionBy) throws SQLException {
        if (tableExists(table)) {
            return false;
        }
        String qualified = qualified(table);
        duckDB.inTransaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE %s (
                            trade_id   VARCHAR NOT NULL,
                            ticker     VARCHAR NOT NULL,
                            price      DECIMAL(12, 2) NOT NULL,
                            quantity   BIGINT NOT NULL,
                            trade_date DATE NOT NULL)
                        """.formatted(qualified));
                if (!partitionBy.isEmpty()) {
                    stmt.execute("ALTER TABLE %s SET PARTITIONED BY (%s)".formatted(qualified, partitionExpressions(partitionBy)));
                }
            }
            return null;
        });
        log.info("Created DuckLake table {} partitioned by {}", qualified, partitionBy);
        return true;
    }

    public boolean tableExists(String table) throws SQLException {
        return !duckDB.executeQuery("SELECT 1 FROM duckdb_tables() WHERE database_name = %s AND table_name = %s"
                .formatted(literal(catalog), literal(identifier(table)))).isEmpty();
    }

    /** Appends trades in one transaction, i.e. one DuckLake snapshot. Nothing is written if any row fails. */
    public long appendTrades(String table, List<Trade> trades) throws SQLException {
        boolean hasVenue = columns(table).contains("venue");
        duckDB.inTransaction(conn -> {
            insert(conn, table, trades, hasVenue);
            return null;
        });
        long snapshotId = currentSnapshotId();
        log.info("Appended {} trades to {} in snapshot {}", trades.size(), table, snapshotId);
        return snapshotId;
    }

    /**
     * Corrects existing trade prices and appends new trades atomically: both changes land in a
     * single snapshot, or neither does.
     */
    public long commitCorrections(String table, Map<String, BigDecimal> priceCorrections, List<Trade> newTrades)
            throws SQLException {
        boolean hasVenue = columns(table).contains("venue");
        duckDB.inTransaction(conn -> {
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE %s SET price = ? WHERE trade_id = ?".formatted(qualified(table)))) {
                for (Map.Entry<String, BigDecimal> correction : priceCorrections.entrySet()) {
                    update.setBigDecimal(1, correction.getValue());
                    update.setString(2, correction.getKey());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Trade not found: " + correction.getKey());
                    }
                }
            }
            insert(conn, table, newTrades, hasVenue);
            return null;
        });
        return currentSnapshotId();
    }

    private void insert(Connection conn, String table, List<Trade> trades, boolean hasVenue) throws SQLException {
        String sql = hasVenue
                ? "INSERT INTO %s (trade_id, ticker, price, quantity, trade_date, venue) VALUES (?, ?, ?, ?, ?, ?)"
                : "INSERT INTO %s (trade_id, ticker, price, quantity, trade_date) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql.formatted(qualified(table)))) {
            for (Trade trade : trades) {
                ps.setString(1, trade.getTradeId());
                ps.setString(2, trade.getTicker());
                ps.setBigDecimal(3, trade.getPrice());
                ps.setLong(4, trade.getQuantity());
                ps.setDate(5, trade.getTradeDate() == null ? null : Date.valueOf(trade.getTradeDate()));
                if (hasVenue) {
                    ps.setString(6, trade.getVenue());
                }
                ps.executeUpdate();
            }
        }
    }

    // ---------------------------------------------------------------- snapshots & time travel

    public long currentSnapshotId() throws SQLException {
        return ((Number) duckDB.executeQuery("SELECT max(snapshot_id) AS id FROM %s.snapshots()".formatted(catalog))
                .getFirst().get("id")).longValue();
    }

    public List<DuckLakeSnapshot> listSnapshots() throws SQLException {
        List<DuckLakeSnapshot> snapshots = new ArrayList<>();
        try (Connection conn = duckDB.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT snapshot_id, snapshot_time, schema_version, changes::VARCHAR AS changes
                     FROM %s.snapshots() ORDER BY snapshot_id""".formatted(catalog))) {
            while (rs.next()) {
                snapshots.add(DuckLakeSnapshot.builder()
                        .snapshotId(rs.getLong("snapshot_id"))
                        .snapshotTime(rs.getObject("snapshot_time", OffsetDateTime.class))
                        .schemaVersion(rs.getLong("schema_version"))
                        .changes(rs.getString("changes"))
                        .build());
            }
        }
        return snapshots;
    }

    /** Current trades, or the trades as of {@code version} when it is not null. */
    public List<Trade> findTrades(String table, Long version) throws SQLException {
        String at = version == null ? "" : " AT (VERSION => %d)".formatted(version);
        return queryTrades("SELECT * FROM %s%s ORDER BY trade_id".formatted(qualified(table), at));
    }

    /** Trades as they were at a point in time; DuckLake resolves the snapshot current at that instant. */
    public List<Trade> findTradesAsOf(String table, OffsetDateTime timestamp) throws SQLException {
        return queryTrades("SELECT * FROM %s AT (TIMESTAMP => TIMESTAMPTZ %s) ORDER BY trade_id"
                .formatted(qualified(table), literal(timestamp.toString())));
    }

    /** Column names of the table as of {@code version}, or currently when it is null. */
    public List<String> columns(String table, Long version) throws SQLException {
        String at = version == null ? "" : " AT (VERSION => %d)".formatted(version);
        return duckDB.executeQuery("DESCRIBE SELECT * FROM %s%s".formatted(qualified(table), at)).stream()
                .map(row -> (String) row.get("column_name"))
                .toList();
    }

    public List<String> columns(String table) throws SQLException {
        return columns(table, null);
    }

    /** Row-level changes committed in snapshots {@code fromSnapshot..toSnapshot} (inclusive). */
    public List<TradeChange> tradeChanges(String table, long fromSnapshot, long toSnapshot) throws SQLException {
        List<TradeChange> changes = new ArrayList<>();
        try (Connection conn = duckDB.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT snapshot_id, change_type, trade_id, price
                     FROM %s.table_changes(%s, %d, %d)
                     ORDER BY snapshot_id, trade_id, change_type"""
                     .formatted(catalog, literal(identifier(table)), fromSnapshot, toSnapshot))) {
            while (rs.next()) {
                changes.add(TradeChange.builder()
                        .snapshotId(rs.getLong("snapshot_id"))
                        .changeType(rs.getString("change_type"))
                        .tradeId(rs.getString("trade_id"))
                        .price(rs.getBigDecimal("price"))
                        .build());
            }
        }
        return changes;
    }

    // ---------------------------------------------------------------- evolution

    /** Adds a nullable column. Existing data files are not rewritten; old rows read it as NULL. */
    public long addColumn(String table, String column, String sqlType) throws SQLException {
        if (!sqlType.matches("[A-Za-z]+(\\(\\d+(, ?\\d+)?\\))?")) {
            throw new IllegalArgumentException("Invalid column type: " + sqlType);
        }
        duckDB.executeStatement("ALTER TABLE %s ADD COLUMN %s %s".formatted(qualified(table), identifier(column), sqlType));
        return currentSnapshotId();
    }

    /** Changes the partition layout for future writes. Existing files keep the layout they were written with. */
    public long setPartitioning(String table, List<String> partitionBy) throws SQLException {
        duckDB.executeStatement("ALTER TABLE %s SET PARTITIONED BY (%s)".formatted(qualified(table), partitionExpressions(partitionBy)));
        return currentSnapshotId();
    }

    // ---------------------------------------------------------------- files, inlining & maintenance

    public List<DuckLakeDataFile> listDataFiles(String table) throws SQLException {
        List<DuckLakeDataFile> files = new ArrayList<>();
        try (Connection conn = duckDB.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT data_file, data_file_size_bytes, delete_file FROM ducklake_list_files(%s, %s)"
                             .formatted(literal(catalog), literal(identifier(table))))) {
            while (rs.next()) {
                files.add(DuckLakeDataFile.builder()
                        .dataFile(rs.getString("data_file"))
                        .dataFileSizeBytes(rs.getLong("data_file_size_bytes"))
                        .deleteFile(rs.getString("delete_file"))
                        .build());
            }
        }
        return files;
    }

    /** Sets the per-table inlining threshold: inserts up to {@code rowLimit} rows are kept in the catalog database. */
    public void setDataInliningRowLimit(String table, int rowLimit) throws SQLException {
        duckDB.executeStatement("CALL %s.set_option('data_inlining_row_limit', %d, table_name => %s)"
                .formatted(catalog, rowLimit, literal(identifier(table))));
    }

    /** Writes rows held in the catalog database out to Parquet data files. */
    public long flushInlinedData(String table) throws SQLException {
        return duckDB.executeQuery("CALL ducklake_flush_inlined_data(%s, table_name => %s)"
                        .formatted(literal(catalog), literal(identifier(table)))).stream()
                .mapToLong(row -> ((Number) row.get("rows_flushed")).longValue())
                .sum();
    }

    /** Merges small adjacent data files of the table into larger ones. The replaced files stay until cleanup. */
    public CompactionResult compact(String table) throws SQLException {
        List<Map<String, Object>> rows = duckDB.executeQuery("CALL ducklake_merge_adjacent_files(%s, %s)"
                .formatted(literal(catalog), literal(identifier(table))));
        long processed = rows.stream().mapToLong(row -> ((Number) row.get("files_processed")).longValue()).sum();
        long created = rows.stream().mapToLong(row -> ((Number) row.get("files_created")).longValue()).sum();
        log.info("Compacted {}: {} files merged into {}", table, processed, created);
        return CompactionResult.builder().filesProcessed(processed).filesCreated(created).build();
    }

    /**
     * Expires every snapshot older than {@code olderThan} across the whole catalog (the current snapshot is always kept).
     * Expired snapshots can no longer be used for time travel.
     *
     * @return the ids of the expired snapshots
     */
    public List<Long> expireSnapshots(OffsetDateTime olderThan) throws SQLException {
        return duckDB.executeQuery("CALL ducklake_expire_snapshots(%s, older_than => TIMESTAMPTZ %s)"
                        .formatted(literal(catalog), literal(olderThan.toString()))).stream()
                .map(row -> ((Number) row.get("snapshot_id")).longValue())
                .toList();
    }

    /**
     * Deletes data files that no remaining snapshot references (e.g. files replaced by compaction
     * whose snapshots have been expired).
     *
     * @return the deleted file paths
     */
    public List<String> cleanupOldFiles() throws SQLException {
        return duckDB.executeQuery("CALL ducklake_cleanup_old_files(%s, cleanup_all => true)"
                        .formatted(literal(catalog))).stream()
                .map(row -> (String) row.get("path"))
                .toList();
    }

    /**
     * Deletes files under the data path that the catalog has never referenced, e.g. Parquet written by a
     * statement whose transaction then failed and rolled back.
     *
     * @return the deleted file paths
     */
    public List<String> deleteOrphanedFiles() throws SQLException {
        return duckDB.executeQuery("CALL ducklake_delete_orphaned_files(%s, cleanup_all => true)"
                        .formatted(literal(catalog))).stream()
                .map(row -> (String) row.get("path"))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private List<Trade> queryTrades(String sql) throws SQLException {
        List<Trade> trades = new ArrayList<>();
        try (Connection conn = duckDB.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            boolean hasVenue = hasColumn(rs, "venue");
            while (rs.next()) {
                trades.add(Trade.builder()
                        .tradeId(rs.getString("trade_id"))
                        .ticker(rs.getString("ticker"))
                        .price(rs.getBigDecimal("price"))
                        .quantity(rs.getLong("quantity"))
                        .tradeDate(rs.getDate("trade_date").toLocalDate())
                        .venue(hasVenue ? rs.getString("venue") : null)
                        .build());
            }
        }
        return trades;
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            if (rs.getMetaData().getColumnLabel(i).equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private String qualified(String table) {
        return catalog + "." + identifier(table);
    }

    private static String partitionExpressions(List<String> partitionBy) {
        for (String expression : partitionBy) {
            if (!PARTITION_EXPRESSION.matcher(expression).matches()) {
                throw new IllegalArgumentException("Invalid partition expression: " + expression);
            }
        }
        return String.join(", ", partitionBy);
    }
}
