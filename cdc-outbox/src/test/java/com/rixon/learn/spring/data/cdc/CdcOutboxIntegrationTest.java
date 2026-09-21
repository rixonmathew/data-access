package com.rixon.learn.spring.data.cdc;

import com.rixon.learn.spring.data.cdc.consumer.IdempotentOrderConsumer;
import com.rixon.learn.spring.data.cdc.model.OutboxEvent;
import com.rixon.learn.spring.data.cdc.service.OrderOutboxService;
import com.rixon.learn.spring.data.cdc.service.OutboxRelayService;
import com.rixon.model.order.Order;
import com.rixon.model.order.OrderSide;
import com.rixon.model.order.OrderStatus;
import com.rixon.model.order.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CdcOutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.1.1"));

    @Autowired
    private OrderOutboxService orderOutboxService;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private IdempotentOrderConsumer orderConsumer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.host", postgres::getHost);
        registry.add("spring.r2dbc.port", postgres::getFirstMappedPort);
        registry.add("spring.r2dbc.database", postgres::getDatabaseName);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        orderConsumer.resetTelemetry();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Atomic Outbox Transaction Commit (Dual-Write Prevention)")
    void testAtomicOutboxCommit() {
        Order order = Order.builder()
                .orderId("ORD-CDC-101")
                .accountNumber("ACC-PRIME-01")
                .ticker("NVDA")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("125.00"))
                .quantity(new BigDecimal("500"))
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        StepVerifier.create(orderOutboxService.createOrderWithOutbox(order, false))
                .assertNext(eventId -> {
                    assertThat(eventId).isNotNull().startsWith("EVT-");
                })
                .verifyComplete();

        StepVerifier.create(orderOutboxService.countOrders())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1L))
                .verifyComplete();

        StepVerifier.create(orderOutboxService.countPendingOutboxEvents())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1L))
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Atomic Rollback on Failure (No Phantom Events)")
    void testAtomicRollbackNoPhantomEvents() {
        long ordersBefore = orderOutboxService.countOrders().block();
        long outboxBefore = orderOutboxService.countOutboxEvents().block();

        Order failingOrder = Order.builder()
                .orderId("ORD-CDC-FAIL-999")
                .accountNumber("ACC-PRIME-01")
                .ticker("TSLA")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("250.00"))
                .quantity(new BigDecimal("100"))
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        StepVerifier.create(orderOutboxService.createOrderWithOutbox(failingOrder, true))
                .expectError(IllegalStateException.class)
                .verify();

        // Verify neither order nor outbox event were persisted
        StepVerifier.create(orderOutboxService.countOrders())
                .assertNext(count -> assertThat(count).isEqualTo(ordersBefore))
                .verifyComplete();

        StepVerifier.create(orderOutboxService.countOutboxEvents())
                .assertNext(count -> assertThat(count).isEqualTo(outboxBefore))
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Outbox Relay Dispatch to Redpanda/Kafka & Status Transition")
    void testOutboxRelayDispatch() {
        Order order2 = Order.builder()
                .orderId("ORD-CDC-102")
                .accountNumber("ACC-PRIME-02")
                .ticker("AAPL")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .price(new BigDecimal("225.50"))
                .quantity(new BigDecimal("1000"))
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderOutboxService.createOrderWithOutbox(order2, false).block();

        // Execute relay
        List<OutboxEvent> relayedEvents = outboxRelayService.relayPendingEvents().collectList().block();
        assertThat(relayedEvents).isNotEmpty();

        assertThat(relayedEvents).allMatch(e -> "PUBLISHED".equals(e.getStatus()));

        // All pending outbox events should now be published
        StepVerifier.create(orderOutboxService.countPendingOutboxEvents())
                .assertNext(pending -> assertThat(pending).isEqualTo(0L))
                .verifyComplete();

        StepVerifier.create(orderOutboxService.countPublishedOutboxEvents())
                .assertNext(published -> assertThat(published).isGreaterThanOrEqualTo(2L))
                .verifyComplete();

        // Verify that the KafkaListener asynchronously consumed the relayed event from Redpanda
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orderConsumer.getTotalReceivedCount()).isGreaterThanOrEqualTo(2L);
        });
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Idempotent Consumer & Deduplication Log Verification")
    void testIdempotentConsumerDeduplication() {
        orderConsumer.resetTelemetry();

        String eventPayload = """
            {
              "eventId": "EVT-DUP-TEST-001",
              "orderId": "ORD-DUP-01",
              "accountNumber": "ACC-001",
              "ticker": "MSFT",
              "side": "BUY",
              "price": 430.00,
              "quantity": 300,
              "timestamp": "2026-09-21T12:00:00Z"
            }
            """;

        // Attempt 1: New message -> must be processed
        Boolean firstProcess = orderConsumer.processMessage(eventPayload).block();
        assertThat(firstProcess).isTrue();
        assertThat(orderConsumer.getDistinctProcessedCount()).isEqualTo(1L);
        assertThat(orderConsumer.getDuplicateFilteredCount()).isEqualTo(0L);

        // Attempt 2: Exact duplicate -> must be skipped & deduplicated
        Boolean secondProcess = orderConsumer.processMessage(eventPayload).block();
        assertThat(secondProcess).isFalse();
        assertThat(orderConsumer.getDistinctProcessedCount()).isEqualTo(1L);
        assertThat(orderConsumer.getDuplicateFilteredCount()).isEqualTo(1L);

        // Attempt 3: Another duplicate -> must be skipped
        Boolean thirdProcess = orderConsumer.processMessage(eventPayload).block();
        assertThat(thirdProcess).isFalse();
        assertThat(orderConsumer.getDistinctProcessedCount()).isEqualTo(1L);
        assertThat(orderConsumer.getDuplicateFilteredCount()).isEqualTo(2L);

        // Verify aggregated streaming OLAP metrics
        assertThat(orderConsumer.getVolumeForTicker("MSFT")).isEqualByComparingTo(new BigDecimal("300"));
    }
}
