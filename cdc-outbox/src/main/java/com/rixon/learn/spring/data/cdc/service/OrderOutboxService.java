package com.rixon.learn.spring.data.cdc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rixon.learn.spring.data.cdc.model.OrderPlacedEvent;
import com.rixon.learn.spring.data.cdc.model.OutboxEvent;
import com.rixon.model.order.Order;
import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OrderOutboxService {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(DatabaseClient databaseClient, TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Executes atomic placement of an order and emission of its OutboxEvent
     * within the same database transaction.
     */
    public Mono<String> createOrderWithOutbox(Order order, boolean simulateFailure) {
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8);

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .eventId(eventId)
                .orderId(order.getOrderId())
                .accountNumber(order.getAccountNumber())
                .ticker(order.getTicker())
                .side(order.getSide().name())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .timestamp(Instant.now())
                .build();

        String insertOrderSql = """
            INSERT INTO orders (order_id, account_number, ticker, side, order_type, price, quantity, status)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            """;

        String insertOutboxSql = """
            INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload, status)
            VALUES ($1, $2, $3, $4, $5::jsonb, $6)
            """;

        Mono<Void> transaction = databaseClient.sql(insertOrderSql)
                .bind("$1", order.getOrderId())
                .bind("$2", order.getAccountNumber())
                .bind("$3", order.getTicker())
                .bind("$4", order.getSide().name())
                .bind("$5", order.getOrderType().name())
                .bind("$6", order.getPrice())
                .bind("$7", order.getQuantity())
                .bind("$8", order.getStatus().name())
                .then()
                .then(Mono.defer(() -> {
                    if (simulateFailure) {
                        log.warn("Simulating intentional transactional failure for order {}", order.getOrderId());
                        return Mono.error(new IllegalStateException("Simulated system failure before outbox commit"));
                    }
                    try {
                        String payloadJson = objectMapper.writeValueAsString(event);
                        return databaseClient.sql(insertOutboxSql)
                                .bind("$1", eventId)
                                .bind("$2", "Order")
                                .bind("$3", order.getOrderId())
                                .bind("$4", "ORDER_CREATED")
                                .bind("$5", payloadJson)
                                .bind("$6", "PENDING")
                                .then();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                }));

        return transaction
                .as(transactionalOperator::transactional)
                .thenReturn(eventId);
    }

    public Mono<Long> countOrders() {
        return databaseClient.sql("SELECT count(*) as cnt FROM orders")
                .map((row, md) -> row.get("cnt", Long.class))
                .one();
    }

    public Mono<Long> countOutboxEvents() {
        return databaseClient.sql("SELECT count(*) as cnt FROM outbox_events")
                .map((row, md) -> row.get("cnt", Long.class))
                .one();
    }

    public Mono<Long> countPendingOutboxEvents() {
        return databaseClient.sql("SELECT count(*) as cnt FROM outbox_events WHERE status = 'PENDING'")
                .map((row, md) -> row.get("cnt", Long.class))
                .one();
    }

    public Mono<Long> countPublishedOutboxEvents() {
        return databaseClient.sql("SELECT count(*) as cnt FROM outbox_events WHERE status = 'PUBLISHED'")
                .map((row, md) -> row.get("cnt", Long.class))
                .one();
    }
}
