package com.rixon.learn.spring.data.questdb.service;

import com.rixon.learn.spring.data.questdb.model.MarketTick;
import com.rixon.learn.spring.data.questdb.model.TradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

@Service
public class QuestDbIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDbIngestionService.class);

    private final JdbcTemplate jdbcTemplate;

    public QuestDbIngestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        LOGGER.info("Initializing QuestDB schema...");
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            LOGGER.info("QuestDB schema initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize QuestDB schema: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void truncateTables() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE market_quotes;");
            jdbcTemplate.execute("TRUNCATE TABLE trade_executions;");
        } catch (Exception ignored) {
        }
    }

    /**
     * Batch insert market quotes via JDBC over PostgreSQL wire protocol.
     */
    public void ingestTicksJdbc(List<MarketTick> ticks) {
        String sql = "INSERT INTO market_quotes (symbol, bid, ask, last_price, volume, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, ticks, ticks.size(), (ps, tick) -> {
            ps.setString(1, tick.getSymbol());
            ps.setDouble(2, tick.getBid());
            ps.setDouble(3, tick.getAsk());
            ps.setDouble(4, tick.getLastPrice());
            ps.setLong(5, tick.getVolume());
            ps.setTimestamp(6, Timestamp.from(tick.getTimestamp()));
        });
        LOGGER.info("Ingested {} market ticks via JDBC.", ticks.size());
    }

    /**
     * Batch insert trades via JDBC over PostgreSQL wire protocol.
     */
    public void ingestTradesJdbc(List<TradeRecord> trades) {
        String sql = "INSERT INTO trade_executions (trade_id, symbol, price, quantity, side, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, trades, trades.size(), (ps, trade) -> {
            ps.setString(1, trade.getTradeId());
            ps.setString(2, trade.getSymbol());
            ps.setDouble(3, trade.getPrice());
            ps.setLong(4, trade.getQuantity());
            ps.setString(5, trade.getSide());
            ps.setTimestamp(6, Timestamp.from(trade.getTimestamp()));
        });
        LOGGER.info("Ingested {} trade records via JDBC.", trades.size());
    }

    /**
     * Ultra-fast InfluxDB Line Protocol (ILP) streaming over TCP socket.
     * High-Frequency Trading (HFT) engines stream quotes directly over ILP port 9009
     * for sub-millisecond, zero-garbage ingestion.
     */
    public void ingestViaIlpSocket(String host, int ilpPort, List<String> ilpLines) {
        try (Socket socket = new Socket(host, ilpPort);
             OutputStream os = socket.getOutputStream()) {
            StringBuilder sb = new StringBuilder();
            for (String line : ilpLines) {
                sb.append(line).append("\n");
            }
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            LOGGER.info("Streamed {} lines via QuestDB ILP TCP socket.", ilpLines.size());
        } catch (Exception e) {
            LOGGER.error("ILP socket ingestion error: {}", e.getMessage(), e);
            throw new RuntimeException("Error streaming ILP to QuestDB: " + e.getMessage(), e);
        }
    }
}
