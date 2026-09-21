package com.rixon.learn.spring.data.trino;

import com.rixon.learn.spring.data.trino.model.FederatedOrderExposure;
import com.rixon.learn.spring.data.trino.model.TickerAggregatedRisk;
import com.rixon.learn.spring.data.trino.service.FederatedQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.utility.DockerImageName;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EnabledIfDockerAvailable
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrinoFederationIntegrationTest {

    private static final Network network = Network.newNetwork();

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static final TrinoContainer trino;

    static {
        // Start PostgreSQL first to ensure it's ready for Trino catalog resolution
        postgres.start();

        // Initialize schema and sample OLTP records in PostgreSQL
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE accounts (
                    account_number VARCHAR(50) PRIMARY KEY,
                    account_name VARCHAR(100) NOT NULL,
                    tier VARCHAR(20) NOT NULL,
                    country VARCHAR(10) NOT NULL
                );

                CREATE TABLE orders (
                    order_id VARCHAR(50) PRIMARY KEY,
                    account_number VARCHAR(50) NOT NULL REFERENCES accounts(account_number),
                    ticker VARCHAR(20) NOT NULL,
                    side VARCHAR(10) NOT NULL,
                    price NUMERIC(18, 4) NOT NULL,
                    quantity NUMERIC(18, 4) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );

                INSERT INTO accounts VALUES
                ('ACC-INST-001', 'Apex Capital Fund', 'PLATINUM', 'USA'),
                ('ACC-INST-002', 'Borealis Global Macro', 'GOLD', 'UK'),
                ('ACC-INST-003', 'Citadel Prime Strategy', 'PLATINUM', 'USA'),
                ('ACC-RETAIL-004', 'Alpha Retail Trader', 'BRONZE', 'DEU');

                INSERT INTO orders VALUES
                ('ORD-TRINO-001', 'ACC-INST-001', 'AAPL', 'BUY', 225.50, 1000, 'USD', CURRENT_TIMESTAMP),
                ('ORD-TRINO-002', 'ACC-INST-001', 'NVDA', 'BUY', 120.00, 2500, 'USD', CURRENT_TIMESTAMP),
                ('ORD-TRINO-003', 'ACC-INST-002', 'ASML', 'BUY', 750.00, 200,  'EUR', CURRENT_TIMESTAMP),
                ('ORD-TRINO-004', 'ACC-INST-003', 'AZN',  'BUY', 65.00,  3000, 'GBP', CURRENT_TIMESTAMP),
                ('ORD-TRINO-005', 'ACC-RETAIL-004', 'SAP', 'BUY', 190.00, 150,  'EUR', CURRENT_TIMESTAMP),
                ('ORD-TRINO-006', 'ACC-INST-001', 'MSFT', 'BUY', 420.00, 500,  'USD', CURRENT_TIMESTAMP);
                """);
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed PostgreSQL data for Trino", e);
        }

        // Configure Trino catalog pointing to the PostgreSQL container on the shared network
        String postgresCatalogConfig = """
            connector.name=postgresql
            connection-url=jdbc:postgresql://postgres:5432/testdb
            connection-user=test
            connection-password=test
            """;

        trino = new TrinoContainer(DockerImageName.parse("trinodb/trino:483"))
                .withNetwork(network)
                .withCopyToContainer(
                        Transferable.of(postgresCatalogConfig.getBytes(StandardCharsets.UTF_8)),
                        "/etc/trino/catalog/postgresql.properties"
                );

        trino.start();
    }

    @Autowired
    private FederatedQueryService federatedQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:trino://" + trino.getHost() + ":" + trino.getMappedPort(8080) + "/memory/default");
        registry.add("spring.datasource.username", () -> "test");
    }

    @BeforeAll
    static void waitForTrinoReady(@Autowired JdbcTemplate jdbcTemplate) {
        // Ensure that Trino worker node is fully registered and active before running distributed queries
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    try {
                        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM tpch.tiny.nation", Long.class);
                        return count != null && count > 0;
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Discovered Catalogs Topology Verification")
    void testDiscoveredCatalogs() {
        List<String> catalogs = federatedQueryService.listCatalogs();
        assertThat(catalogs).contains("postgresql", "memory", "tpch", "system");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Initialize In-Memory Catalog Reference Table")
    void testInitializeMemoryCatalog() {
        federatedQueryService.initializeMemoryCatalog();

        // Verify that memory.default.fx_rates can be queried
        List<FederatedOrderExposure> exposures = federatedQueryService.queryFederatedOrderExposures();
        assertThat(exposures).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Cross-Engine Federated JOIN across PostgreSQL and Memory Catalog")
    void testCrossEngineFederatedJoin() {
        List<FederatedOrderExposure> exposures = federatedQueryService.queryFederatedOrderExposures();

        // Must join all 6 orders
        assertThat(exposures).hasSize(6);

        // Find ASML order in EUR (750.00 * 200 * 1.085000 = 162750 USD)
        FederatedOrderExposure asmlOrder = exposures.stream()
                .filter(e -> "ASML".equals(e.getTicker()))
                .findFirst()
                .orElseThrow();

        assertThat(asmlOrder.getAccountName()).isEqualTo("Borealis Global Macro");
        assertThat(asmlOrder.getTier()).isEqualTo("GOLD");
        assertThat(asmlOrder.getCurrency()).isEqualTo("EUR");
        assertThat(asmlOrder.getFxRateToUsd()).isEqualByComparingTo(new BigDecimal("1.085000"));
        assertThat(asmlOrder.getNotionalUsd()).isEqualByComparingTo(new BigDecimal("162750.0000"));

        // Find AZN order in GBP (65.00 * 3000 * 1.295000 = 252525 USD)
        FederatedOrderExposure aznOrder = exposures.stream()
                .filter(e -> "AZN".equals(e.getTicker()))
                .findFirst()
                .orElseThrow();

        assertThat(aznOrder.getAccountName()).isEqualTo("Citadel Prime Strategy");
        assertThat(aznOrder.getCurrency()).isEqualTo("GBP");
        assertThat(aznOrder.getNotionalUsd()).isEqualByComparingTo(new BigDecimal("252525.0000"));

        // Find NVDA order in USD (120.00 * 2500 * 1.0 = 300000 USD)
        FederatedOrderExposure nvdaOrder = exposures.stream()
                .filter(e -> "NVDA".equals(e.getTicker()))
                .findFirst()
                .orElseThrow();

        assertThat(nvdaOrder.getNotionalUsd()).isEqualByComparingTo(new BigDecimal("300000.0000"));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Distributed Risk Aggregation & Predicate Pushdown for PLATINUM Tier")
    void testFederatedRiskAggregationByTier() {
        // Aggregates only PLATINUM accounts: ACC-INST-001 (AAPL, NVDA, MSFT) and ACC-INST-003 (AZN)
        List<TickerAggregatedRisk> platinumRisks = federatedQueryService.queryAggregatedRiskByTicker("PLATINUM");

        assertThat(platinumRisks).hasSize(4);

        // NVDA has 300,000 USD notional
        TickerAggregatedRisk nvdaRisk = platinumRisks.stream()
                .filter(r -> "NVDA".equals(r.getTicker()))
                .findFirst()
                .orElseThrow();
        assertThat(nvdaRisk.getTotalOrders()).isEqualTo(1);
        assertThat(nvdaRisk.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("2500.0000"));
        assertThat(nvdaRisk.getTotalNotionalUsd()).isEqualByComparingTo(new BigDecimal("300000.0000"));

        // AZN has 252,525 USD notional
        TickerAggregatedRisk aznRisk = platinumRisks.stream()
                .filter(r -> "AZN".equals(r.getTicker()))
                .findFirst()
                .orElseThrow();
        assertThat(aznRisk.getTotalNotionalUsd()).isEqualByComparingTo(new BigDecimal("252525.0000"));

        // Verify GOLD accounts (e.g. ASML) are NOT included in PLATINUM aggregation
        assertThat(platinumRisks).noneMatch(r -> "ASML".equals(r.getTicker()));
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Built-in TPC-H Analytical Benchmark Federation")
    void testTpchAnalyticalBenchmark() {
        List<Map<String, Object>> europeanNations = federatedQueryService.queryTpchNationBenchmark("EUROPE");

        assertThat(europeanNations).isNotEmpty();
        List<String> nationNames = europeanNations.stream()
                .map(m -> (String) m.get("nation_name"))
                .toList();

        assertThat(nationNames).contains("GERMANY", "FRANCE", "UNITED KINGDOM", "ROMANIA", "RUSSIA");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Distributed Query Execution Plan Verification")
    void testExplainQueryPlan() {
        String federatedSql = """
            SELECT o.order_id, a.account_name, fx.fx_rate
            FROM postgresql.public.orders o
            JOIN postgresql.public.accounts a ON o.account_number = a.account_number
            JOIN memory.default.fx_rates fx ON o.currency = fx.currency
            """;

        String plan = federatedQueryService.explainQueryPlan(federatedSql);
        assertThat(plan).isNotEmpty();
        // The plan will show scans on postgresql connector and memory connector, and distributed join
        assertThat(plan).contains("ScanFilter").contains("InnerJoin");
    }
}
