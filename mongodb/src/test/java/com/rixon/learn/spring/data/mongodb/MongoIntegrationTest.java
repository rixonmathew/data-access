package com.rixon.learn.spring.data.mongodb;

import com.rixon.learn.spring.data.mongodb.document.ExecutionStrategy;
import com.rixon.learn.spring.data.mongodb.document.OrderDocument;
import com.rixon.learn.spring.data.mongodb.dto.TickerOrderSummary;
import com.rixon.learn.spring.data.mongodb.repository.OrderDocumentRepository;
import com.rixon.learn.spring.data.mongodb.service.OrderDocumentService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class MongoIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> mongo.getConnectionString() + "/trading_db");
        registry.add("spring.mongodb.database", () -> "trading_db");
    }

    @Autowired
    private OrderDocumentService orderService;

    @Autowired
    private OrderDocumentRepository orderRepository;

    @BeforeEach
    void cleanCollection() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("MongoDB Scenario 1: Embedded Subdocuments & Audit Trail Lifecycle")
    void testNestedDocumentModelingAndAuditTrail() {
        OrderDocument order = OrderDocument.builder()
                .orderId("ORD-MNG-001")
                .accountNumber("ACC-MNG-100")
                .ticker("NVDA")
                .side("BUY")
                .orderType("LIMIT")
                .price(new BigDecimal("135.50"))
                .quantity(new BigDecimal("500"))
                .status("PENDING_ROUTING")
                .strategy(ExecutionStrategy.builder()
                        .strategyType("TWAP")
                        .sliceCount(10)
                        .durationMinutes(30)
                        .participationRate(0.15)
                        .parameters(Map.of("maxSlippageBps", 5, "venue", "DARK_POOL"))
                        .build())
                .customAttributes(Map.of("complianceCheckId", "CMP-9988", "desk", "ALGO_EQUITY"))
                .build();

        order.addAuditEntry("CREATED", "trader_alice", "Initial order entry");
        order.addAuditEntry("ALGO_CONFIGURED", "system_algo_engine", "Applied TWAP parameters");

        OrderDocument saved = orderService.save(order);
        assertThat(saved.getId()).isNotNull();

        // Query by embedded strategy type
        List<OrderDocument> twapOrders = orderService.findByStrategyType("TWAP");
        assertThat(twapOrders).hasSize(1);

        OrderDocument found = twapOrders.get(0);
        assertThat(found.getOrderId()).isEqualTo("ORD-MNG-001");
        assertThat(found.getStrategy().getStrategyType()).isEqualTo("TWAP");
        assertThat(found.getStrategy().getParameters().get("venue")).isEqualTo("DARK_POOL");
        assertThat(found.getAuditTrail()).hasSize(2);
        assertThat(found.getAuditTrail().get(0).getAction()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("MongoDB Scenario 2: Multi-Stage Aggregation Pipeline ($match -> $group -> $project -> $sort)")
    void testMultiStageAggregationPipeline() {
        List<OrderDocument> batch = new ArrayList<>();

        // 3 AAPL buy orders: total qty = 300
        batch.add(createOrder("ORD-A1", "AAPL", "BUY", "150.00", "100"));
        batch.add(createOrder("ORD-A2", "AAPL", "BUY", "155.00", "100"));
        batch.add(createOrder("ORD-A3", "AAPL", "BUY", "160.00", "100"));

        // 2 MSFT buy orders: total qty = 500
        batch.add(createOrder("ORD-M1", "MSFT", "BUY", "400.00", "200"));
        batch.add(createOrder("ORD-M2", "MSFT", "BUY", "410.00", "300"));

        // 1 GOOGL buy order: total qty = 50
        batch.add(createOrder("ORD-G1", "GOOGL", "BUY", "175.00", "50"));

        // Sell orders (should be filtered out by $match side = 'BUY')
        batch.add(createOrder("ORD-S1", "AAPL", "SELL", "150.00", "999"));

        orderService.saveAll(batch);

        // Execute aggregation pipeline
        List<TickerOrderSummary> summaries = orderService.calculateTickerSummaries("BUY");

        assertThat(summaries).hasSize(3);

        // MSFT has highest volume (500) -> should be first due to sort
        TickerOrderSummary first = summaries.get(0);
        assertThat(first.getTicker()).isEqualTo("MSFT");
        assertThat(first.getTotalOrders()).isEqualTo(2);
        assertThat(first.getTotalVolume()).isEqualByComparingTo("500");

        // AAPL has second volume (300)
        TickerOrderSummary second = summaries.get(1);
        assertThat(second.getTicker()).isEqualTo("AAPL");
        assertThat(second.getTotalOrders()).isEqualTo(3);
        assertThat(second.getTotalVolume()).isEqualByComparingTo("300");

        // GOOGL has third volume (50)
        TickerOrderSummary third = summaries.get(2);
        assertThat(third.getTicker()).isEqualTo("GOOGL");
        assertThat(third.getTotalOrders()).isEqualTo(1);
        assertThat(third.getTotalVolume()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("MongoDB Scenario 3: Multi-Faceted Aggregation ($facet)")
    void testMultiFacetedAggregationFacet() {
        List<OrderDocument> batch = List.of(
                createOrderWithDetails("ORD-F1", "NEW", "LIMIT"),
                createOrderWithDetails("ORD-F2", "NEW", "MARKET"),
                createOrderWithDetails("ORD-F3", "FILLED", "LIMIT")
        );
        orderService.saveAll(batch);

        Document facetResult = orderService.executeOrderDistributionFacet();

        assertThat(facetResult).isNotNull();
        assertThat(facetResult.containsKey("statusDistribution")).isTrue();
        assertThat(facetResult.containsKey("typeDistribution")).isTrue();

        List<?> statusList = (List<?>) facetResult.get("statusDistribution");
        assertThat(statusList).hasSize(2); // NEW and FILLED

        List<?> typeList = (List<?>) facetResult.get("typeDistribution");
        assertThat(typeList).hasSize(2); // LIMIT and MARKET
    }

    private OrderDocument createOrder(String id, String ticker, String side, String price, String qty) {
        return OrderDocument.builder()
                .orderId(id)
                .accountNumber("ACC-MNG-TEST")
                .ticker(ticker)
                .side(side)
                .orderType("LIMIT")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .status("NEW")
                .build();
    }

    private OrderDocument createOrderWithDetails(String id, String status, String orderType) {
        return OrderDocument.builder()
                .orderId(id)
                .accountNumber("ACC-MNG-TEST")
                .ticker("AAPL")
                .side("BUY")
                .orderType(orderType)
                .price(new BigDecimal("150.00"))
                .quantity(new BigDecimal("100"))
                .status(status)
                .build();
    }
}
