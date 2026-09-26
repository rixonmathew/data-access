package com.rixon.learn.spring.data.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.*;
import com.rixon.learn.spring.data.spanner.model.AccountHierarchySummary;
import com.rixon.learn.spring.data.spanner.model.CustomerOrder;
import com.rixon.learn.spring.data.spanner.model.TradingAccount;
import com.rixon.learn.spring.data.spanner.service.SpannerTradingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class SpannerIntegrationTest {

    static GenericContainer<?> spannerEmulator = new GenericContainer<>(
            DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest")
    )
            .withExposedPorts(9010, 9020)
            .waitingFor(Wait.forListeningPort());

    static {
        spannerEmulator.start();

        SpannerOptions options = SpannerOptions.newBuilder()
                .setProjectId("test-project")
                .setEmulatorHost(spannerEmulator.getHost() + ":" + spannerEmulator.getMappedPort(9010))
                .build();

        try (Spanner spanner = options.getService()) {
            InstanceAdminClient instanceAdminClient = spanner.getInstanceAdminClient();
            InstanceConfigId configId = InstanceConfigId.of("test-project", "emulator-config");
            InstanceId instanceId = InstanceId.of("test-project", "test-instance");

            instanceAdminClient.createInstance(
                    InstanceInfo.newBuilder(instanceId)
                            .setDisplayName("Test Instance")
                            .setInstanceConfigId(configId)
                            .setNodeCount(1)
                            .build()
            ).get();

            DatabaseAdminClient dbAdminClient = spanner.getDatabaseAdminClient();
            List<String> ddl = List.of(
                    "CREATE TABLE TradingAccounts (" +
                    "  account_id STRING(64) NOT NULL," +
                    "  account_name STRING(128) NOT NULL," +
                    "  currency STRING(3) NOT NULL," +
                    "  balance NUMERIC NOT NULL," +
                    "  created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)" +
                    ") PRIMARY KEY (account_id)",

                    "CREATE TABLE CustomerOrders (" +
                    "  account_id STRING(64) NOT NULL," +
                    "  order_id STRING(64) NOT NULL," +
                    "  symbol STRING(16) NOT NULL," +
                    "  side STRING(8) NOT NULL," +
                    "  price NUMERIC NOT NULL," +
                    "  quantity INT64 NOT NULL," +
                    "  status STRING(16) NOT NULL," +
                    "  created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)" +
                    ") PRIMARY KEY (account_id, order_id)," +
                    "  INTERLEAVE IN PARENT TradingAccounts ON DELETE CASCADE",

                    "CREATE TABLE TradeExecutions (" +
                    "  account_id STRING(64) NOT NULL," +
                    "  order_id STRING(64) NOT NULL," +
                    "  execution_id STRING(64) NOT NULL," +
                    "  execution_price NUMERIC NOT NULL," +
                    "  executed_quantity INT64 NOT NULL," +
                    "  executed_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)" +
                    ") PRIMARY KEY (account_id, order_id, execution_id)," +
                    "  INTERLEAVE IN PARENT CustomerOrders ON DELETE CASCADE"
            );

            dbAdminClient.createDatabase("test-instance", "capital-markets-db", ddl).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Spanner emulator instance/database", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spanner.emulator-host", () ->
                String.format("%s:%d", spannerEmulator.getHost(), spannerEmulator.getMappedPort(9010)));
        registry.add("spanner.project-id", () -> "test-project");
        registry.add("spanner.instance-id", () -> "test-instance");
        registry.add("spanner.database-id", () -> "capital-markets-db");
    }

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private SpannerTradingService spannerTradingService;

    @Test
    @DisplayName("Verify TrueTime atomic trade execution across parent-child interleaved tables")
    void testAtomicTradeExecutionAcrossInterleavedHierarchy() {
        // 1. Create TradingAccount with $1,000,000 USD
        TradingAccount account = TradingAccount.builder()
                .accountId("ACC-HFT-01")
                .accountName("Alpha Prime Fund")
                .currency("USD")
                .balance(new BigDecimal("1000000.00"))
                .build();
        spannerTradingService.createAccount(account);

        // 2. Place CustomerOrder to BUY 1,000 shares of NVDA @ $120.00
        CustomerOrder order = CustomerOrder.builder()
                .accountId("ACC-HFT-01")
                .orderId("ORD-NVDA-100")
                .symbol("NVDA")
                .side("BUY")
                .price(new BigDecimal("120.00"))
                .quantity(1000L)
                .status("PENDING")
                .build();
        spannerTradingService.placeOrder(order);

        // 3. Atomically execute the trade: inserts execution, updates order to FILLED, debits $120,000 from account
        Timestamp commitTimestamp = spannerTradingService.executeTradeAtomic(
                "ACC-HFT-01",
                "ORD-NVDA-100",
                "EXEC-NVDA-001",
                new BigDecimal("120.00"),
                1000L
        );

        assertThat(commitTimestamp).isNotNull();

        // 4. Verify Account Hierarchy Summary
        AccountHierarchySummary summary = spannerTradingService.getAccountHierarchySummary("ACC-HFT-01");
        assertThat(summary).isNotNull();
        assertThat(summary.getAccountId()).isEqualTo("ACC-HFT-01");
        assertThat(summary.getBalance()).isEqualByComparingTo(new BigDecimal("880000.00")); // 1M - 120k
        assertThat(summary.getOrderCount()).isEqualTo(1L);
        assertThat(summary.getExecutionCount()).isEqualTo(1L);
        assertThat(summary.getTotalExecutedQuantity()).isEqualTo(1000L);
        assertThat(summary.getTotalExecutedNotional()).isEqualByComparingTo(new BigDecimal("120000.00"));
    }

    @Test
    @DisplayName("Verify Spanner Interleaved CASCADE DELETE deletes child orders and executions automatically")
    void testInterleavedCascadeDelete() {
        TradingAccount account = TradingAccount.builder()
                .accountId("ACC-TEMP-02")
                .accountName("Temporary Desk")
                .currency("USD")
                .balance(new BigDecimal("50000.00"))
                .build();
        spannerTradingService.createAccount(account);

        CustomerOrder order = CustomerOrder.builder()
                .accountId("ACC-TEMP-02")
                .orderId("ORD-TEMP-01")
                .symbol("MSFT")
                .side("BUY")
                .price(new BigDecimal("400.00"))
                .quantity(100L)
                .status("PENDING")
                .build();
        spannerTradingService.placeOrder(order);

        spannerTradingService.executeTradeAtomic(
                "ACC-TEMP-02",
                "ORD-TEMP-01",
                "EXEC-TEMP-01",
                new BigDecimal("400.00"),
                100L
        );

        assertThat(spannerTradingService.getAccountHierarchySummary("ACC-TEMP-02")).isNotNull();

        // Delete parent account; Spanner cascaded delete removes child orders and executions
        spannerTradingService.deleteAccountCascade("ACC-TEMP-02");

        assertThat(spannerTradingService.getAccountHierarchySummary("ACC-TEMP-02")).isNull();

        // Verify zero child executions left in Spanner
        Statement statement = Statement.newBuilder(
                "SELECT COUNT(*) as count FROM TradeExecutions WHERE account_id = 'ACC-TEMP-02'"
        ).build();
        try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("count")).isEqualTo(0L);
        }
    }
}
