package com.rixon.learn.spring.data.dataaccess;

import com.rixon.learn.spring.data.dataaccess.model.OrderPaxosRecord;
import com.rixon.learn.spring.data.dataaccess.model.TradeExecutionKey;
import com.rixon.learn.spring.data.dataaccess.model.TradeExecutionRecord;
import com.rixon.learn.spring.data.dataaccess.service.CassandraTradingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class CassandraIntegrationTest {

    @Container
    static CassandraContainer cassandra = new CassandraContainer("cassandra:4.1");

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) throws Exception {
        cassandra.execInContainer("cqlsh", "-e",
                "CREATE KEYSPACE IF NOT EXISTS trading_store WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }; " +
                "CREATE TABLE IF NOT EXISTS trading_store.trade_executions (" +
                "ticker text, bucket_date date, execution_time timestamp, execution_id text, " +
                "order_id text, account_number text, price decimal, quantity decimal, liquidity_indicator text, venue text, " +
                "PRIMARY KEY ((ticker, bucket_date), execution_time, execution_id)" +
                ") WITH CLUSTERING ORDER BY (execution_time DESC, execution_id ASC); " +
                "CREATE TABLE IF NOT EXISTS trading_store.orders_paxos (" +
                "order_id text PRIMARY KEY, account_number text, ticker text, side text, quantity decimal, price decimal, status text, version bigint);"
        );

        registry.add("spring.cassandra.contact-points", () -> cassandra.getContactPoint().getHostString());
        registry.add("spring.cassandra.port", () -> cassandra.getContactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> "trading_store");
    }

    @Autowired
    private CassandraTradingService tradingService;

    @Test
    @DisplayName("Cassandra Scenario 1: Time-Series Partitioning & Descending Clustering Order")
    void testTimeSeriesClusteringOrder() {
        String ticker = "NVDA";
        LocalDate bucketDate = LocalDate.now();
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Instant t1 = baseTime.minusSeconds(240);
        Instant t2 = baseTime.minusSeconds(180);
        Instant t3 = baseTime.minusSeconds(120);
        Instant t4 = baseTime.minusSeconds(60);
        Instant t5 = baseTime;

        // Insert in arbitrary order
        tradingService.saveExecution(createExecution(ticker, bucketDate, t2, "EXEC-002", "130.00", "50"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t5, "EXEC-005", "131.50", "200"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t1, "EXEC-001", "129.50", "100"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t4, "EXEC-004", "131.00", "150"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t3, "EXEC-003", "130.50", "75"));

        // Query by partition key (ticker, bucketDate)
        List<TradeExecutionRecord> records = tradingService.getExecutionsByBucket(ticker, bucketDate);

        assertThat(records).hasSize(5);

        // Verify clustering order: descending by execution_time (t5, t4, t3, t2, t1)
        assertThat(records.get(0).getKey().getExecutionId()).isEqualTo("EXEC-005");
        assertThat(records.get(1).getKey().getExecutionId()).isEqualTo("EXEC-004");
        assertThat(records.get(2).getKey().getExecutionId()).isEqualTo("EXEC-003");
        assertThat(records.get(3).getKey().getExecutionId()).isEqualTo("EXEC-002");
        assertThat(records.get(4).getKey().getExecutionId()).isEqualTo("EXEC-001");
    }

    @Test
    @DisplayName("Cassandra Scenario 2: Time Window Range Query Within Partition")
    void testTimeSeriesTimeWindowQuery() {
        String ticker = "AAPL";
        LocalDate bucketDate = LocalDate.now();
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Instant t1 = baseTime.minusSeconds(300);
        Instant t2 = baseTime.minusSeconds(200);
        Instant t3 = baseTime.minusSeconds(100);
        Instant t4 = baseTime;

        tradingService.saveExecution(createExecution(ticker, bucketDate, t1, "EXEC-AP-1", "180.00", "10"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t2, "EXEC-AP-2", "181.00", "20"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t3, "EXEC-AP-3", "182.00", "30"));
        tradingService.saveExecution(createExecution(ticker, bucketDate, t4, "EXEC-AP-4", "183.00", "40"));

        // Window: [t2, t3]
        List<TradeExecutionRecord> window = tradingService.getExecutionsInWindow(ticker, bucketDate, t2, t3);

        assertThat(window).hasSize(2);
        assertThat(window.get(0).getKey().getExecutionId()).isEqualTo("EXEC-AP-3"); // Descending order
        assertThat(window.get(1).getKey().getExecutionId()).isEqualTo("EXEC-AP-2");
    }

    @Test
    @DisplayName("Cassandra Scenario 3: Paxos LWT (Lightweight Transactions) - IF NOT EXISTS Idempotency")
    void testPaxosInsertIfNotExists() {
        String orderId = "ORD-LWT-100";
        OrderPaxosRecord order = OrderPaxosRecord.builder()
                .orderId(orderId)
                .accountNumber("ACC-CAS-001")
                .ticker("MSFT")
                .side("BUY")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("420.00"))
                .status("PENDING")
                .version(1L)
                .build();

        // 1st insertion must be applied
        boolean firstApplied = tradingService.submitOrderIfNotExists(order);
        assertThat(firstApplied).isTrue();

        // 2nd insertion with same ID must NOT be applied (Paxos reject)
        OrderPaxosRecord duplicate = OrderPaxosRecord.builder()
                .orderId(orderId)
                .accountNumber("ACC-CAS-002")
                .ticker("GOOGL")
                .side("SELL")
                .quantity(new BigDecimal("50"))
                .price(new BigDecimal("170.00"))
                .status("PENDING")
                .version(1L)
                .build();

        boolean secondApplied = tradingService.submitOrderIfNotExists(duplicate);
        assertThat(secondApplied).isFalse();

        // Check original order unchanged
        Optional<OrderPaxosRecord> saved = tradingService.findOrderById(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTicker()).isEqualTo("MSFT");
    }

    @Test
    @DisplayName("Cassandra Scenario 4: Paxos LWT Conditional Update - Race Condition Guard")
    void testPaxosConditionalStatusUpdate() {
        String orderId = "ORD-LWT-200";
        OrderPaxosRecord order = OrderPaxosRecord.builder()
                .orderId(orderId)
                .accountNumber("ACC-CAS-003")
                .ticker("AMZN")
                .side("BUY")
                .quantity(new BigDecimal("250"))
                .price(new BigDecimal("185.00"))
                .status("PENDING")
                .version(1L)
                .build();

        tradingService.submitOrderIfNotExists(order);

        // 1. Transition PENDING -> FILLED succeeds
        boolean filledApplied = tradingService.transitionOrderStatus(orderId, "PENDING", "FILLED", 2L);
        assertThat(filledApplied).isTrue();

        // 2. Conflicting transition attempting PENDING -> CANCELLED fails because status is now FILLED
        boolean cancelApplied = tradingService.transitionOrderStatus(orderId, "PENDING", "CANCELLED", 3L);
        assertThat(cancelApplied).isFalse();

        // Verify order remains FILLED with version 2
        Optional<OrderPaxosRecord> current = tradingService.findOrderById(orderId);
        assertThat(current).isPresent();
        assertThat(current.get().getStatus()).isEqualTo("FILLED");
        assertThat(current.get().getVersion()).isEqualTo(2L);
    }

    private TradeExecutionRecord createExecution(String ticker, LocalDate bucketDate, Instant time,
                                                String execId, String price, String qty) {
        return TradeExecutionRecord.builder()
                .key(TradeExecutionKey.builder()
                        .ticker(ticker)
                        .bucketDate(bucketDate)
                        .executionTime(time)
                        .executionId(execId)
                        .build())
                .orderId("ORD-" + execId)
                .accountNumber("ACC-TRADER-1")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .liquidityIndicator("MAKER")
                .venue("NASDAQ")
                .build();
    }
}
