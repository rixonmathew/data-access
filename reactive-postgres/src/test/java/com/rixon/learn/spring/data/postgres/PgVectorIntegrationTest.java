package com.rixon.learn.spring.data.postgres;

import com.rixon.learn.spring.data.postgres.model.MarketResearchReport;
import com.rixon.learn.spring.data.postgres.model.OrderWithResearchInsight;
import com.rixon.learn.spring.data.postgres.model.VectorSearchResult;
import com.rixon.learn.spring.data.postgres.repository.OrderReactiveRepository;
import com.rixon.learn.spring.data.postgres.service.PgVectorResearchService;
import com.rixon.model.order.Order;
import com.rixon.model.order.OrderSide;
import com.rixon.model.order.OrderStatus;
import com.rixon.model.order.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class PgVectorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PgVectorResearchService vectorService;

    @Autowired
    private OrderReactiveRepository orderRepository;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        registry.add("spring.r2dbc.host", postgres::getHost);
        registry.add("spring.r2dbc.port", postgres::getFirstMappedPort);
        registry.add("spring.r2dbc.database", postgres::getDatabaseName);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    private final List<MarketResearchReport> sampleReports = List.of(
            MarketResearchReport.builder()
                    .id("R-101")
                    .ticker("NVDA")
                    .title("Next-Gen Blackwell AI GPU Architecture & Data Center Surge")
                    .summary("Massive hyperscaler demand driving GPU rack-scale compute adoption.")
                    .sector("Semiconductors")
                    .sentiment("BULLISH")
                    .confidenceScore(0.98)
                    .embedding(new float[]{0.92f, 0.15f, 0.88f, 0.95f})
                    .build(),
            MarketResearchReport.builder()
                    .id("R-102")
                    .ticker("AMD")
                    .title("MI300X Accelerator Hyperscaler Footprint Expansion")
                    .summary("Competitive alternative in large language model training and inference.")
                    .sector("Semiconductors")
                    .sentiment("BULLISH")
                    .confidenceScore(0.89)
                    .embedding(new float[]{0.85f, 0.18f, 0.82f, 0.88f})
                    .build(),
            MarketResearchReport.builder()
                    .id("R-103")
                    .ticker("AAPL")
                    .title("Vision Pro Ecosystem & Consumer Upgrade Cycles")
                    .summary("Spatial computing headset enterprise adoption remains measured.")
                    .sector("Consumer Electronics")
                    .sentiment("NEUTRAL")
                    .confidenceScore(0.72)
                    .embedding(new float[]{0.35f, 0.80f, 0.40f, 0.60f})
                    .build(),
            MarketResearchReport.builder()
                    .id("R-104")
                    .ticker("MSFT")
                    .title("Azure Copilot Subscriptions & Enterprise Cloud Growth")
                    .summary("Strong generative AI workload growth expanding cloud operating margins.")
                    .sector("Cloud Software")
                    .sentiment("BULLISH")
                    .confidenceScore(0.94)
                    .embedding(new float[]{0.78f, 0.50f, 0.90f, 0.85f})
                    .build(),
            MarketResearchReport.builder()
                    .id("R-105")
                    .ticker("XOM")
                    .title("Global Crude Refining Margins & Upstream Capex")
                    .summary("Softening diesel demand and geopolitical supply risks impact cash flow.")
                    .sector("Energy")
                    .sentiment("BEARISH")
                    .confidenceScore(0.81)
                    .embedding(new float[]{0.10f, 0.12f, 0.20f, 0.15f})
                    .build(),
            MarketResearchReport.builder()
                    .id("R-106")
                    .ticker("INTC")
                    .title("Foundry 18A Node Transition & Legacy PC Margins")
                    .summary("High capital expenditures and manufacturing yield headwinds pressure earnings.")
                    .sector("Semiconductors")
                    .sentiment("BEARISH")
                    .confidenceScore(0.85)
                    .embedding(new float[]{0.60f, 0.10f, 0.30f, 0.40f})
                    .build()
    );

    @BeforeEach
    void setUp() {
        vectorService.insertReports(sampleReports).blockLast();
    }

    @Test
    @DisplayName("Test 1: Dense Vector Ingestion & Record Count Verification")
    void testVectorIngestion() {
        StepVerifier.create(vectorService.countReports())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(6L))
                .verifyComplete();
    }

    @Test
    @DisplayName("Test 2: kNN Cosine Similarity Search (<=>) via HNSW Index")
    void testCosineSimilaritySearch() {
        // Query theme: Generative AI & High-Performance GPU compute
        float[] aiGpuQuery = new float[]{0.90f, 0.14f, 0.85f, 0.92f};

        StepVerifier.create(vectorService.findSimilarByCosine(aiGpuQuery, 3).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(3);

                    VectorSearchResult topMatch = results.get(0);
                    assertThat(topMatch.getTicker()).isEqualTo("NVDA");
                    assertThat(topMatch.getSimilarity()).isGreaterThan(0.98);

                    VectorSearchResult secondMatch = results.get(1);
                    assertThat(secondMatch.getTicker()).isEqualTo("AMD");
                    assertThat(secondMatch.getSimilarity()).isGreaterThan(0.95);

                    VectorSearchResult thirdMatch = results.get(2);
                    assertThat(thirdMatch.getTicker()).isEqualTo("MSFT");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Test 3: kNN Euclidean Distance Search (<->) Exact Vector Match")
    void testL2DistanceSearch() {
        // Query using exact NVDA embedding
        float[] exactNvdaVector = new float[]{0.92f, 0.15f, 0.88f, 0.95f};

        StepVerifier.create(vectorService.findSimilarByL2Distance(exactNvdaVector, 2).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(2);
                    assertThat(results.get(0).getTicker()).isEqualTo("NVDA");
                    assertThat(results.get(0).getDistance()).isLessThan(0.001); // Near zero distance for exact match
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Test 4: Hybrid Relational SQL Predicates + Dense Vector Similarity")
    void testHybridRelationalVectorSearch() {
        // Search AI query but filter strictly to BULLISH Semiconductors
        float[] aiQuery = new float[]{0.85f, 0.20f, 0.80f, 0.85f};

        StepVerifier.create(vectorService.findSimilarWithHybridFilter(aiQuery, "Semiconductors", "BULLISH", 5).collectList())
                .assertNext(results -> {
                    assertThat(results).hasSize(2);
                    List<String> tickers = results.stream().map(VectorSearchResult::getTicker).toList();
                    assertThat(tickers).containsExactlyInAnyOrder("NVDA", "AMD");

                    // INTC is filtered out because it is BEARISH
                    // MSFT is filtered out because it is Cloud Software
                    assertThat(tickers).doesNotContain("INTC", "MSFT", "XOM", "AAPL");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Test 5: Cross-Domain Relational JOIN between OLTP Orders and Vector Research Insights")
    void testRelationalJoinWithOrders() {
        // 1. Seed customer orders for NVDA and MSFT
        Order order1 = Order.builder()
                .orderId("ORD-VEC-001")
                .accountNumber("ACC-TEST-100")
                .ticker("NVDA")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("125.50"))
                .quantity(new BigDecimal("500.00"))
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order order2 = Order.builder()
                .orderId("ORD-VEC-002")
                .accountNumber("ACC-TEST-200")
                .ticker("MSFT")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("430.00"))
                .quantity(new BigDecimal("200.00"))
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderRepository.saveAll(List.of(order1, order2)).collectList().block();

        // 2. Perform relational JOIN matching orders with research reports ranked by AI theme
        float[] aiTheme = new float[]{0.90f, 0.15f, 0.85f, 0.90f};

        StepVerifier.create(vectorService.correlateOrdersWithResearch(aiTheme, 5).collectList())
                .assertNext(joinedResults -> {
                    assertThat(joinedResults).isNotEmpty();

                    OrderWithResearchInsight nvdaJoin = joinedResults.stream()
                            .filter(j -> "NVDA".equals(j.getTicker()))
                            .findFirst()
                            .orElseThrow();

                    assertThat(nvdaJoin.getOrderId()).isEqualTo("ORD-VEC-001");
                    assertThat(nvdaJoin.getSentiment()).isEqualTo("BULLISH");
                    assertThat(nvdaJoin.getReportTitle()).contains("Blackwell");
                    assertThat(nvdaJoin.getVectorSimilarity()).isGreaterThan(0.95);
                })
                .verifyComplete();
    }
}
