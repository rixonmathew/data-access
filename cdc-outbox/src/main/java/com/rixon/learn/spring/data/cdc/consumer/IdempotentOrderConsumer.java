package com.rixon.learn.spring.data.cdc.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rixon.learn.spring.data.cdc.config.KafkaConfig;
import com.rixon.learn.spring.data.cdc.model.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class IdempotentOrderConsumer {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    private final AtomicLong totalReceivedCount = new AtomicLong(0);
    private final AtomicLong distinctProcessedCount = new AtomicLong(0);
    private final AtomicLong duplicateFilteredCount = new AtomicLong(0);

    // In-Memory real-time OLAP state aggregated from the stream
    private final ConcurrentMap<String, BigDecimal> volumeByTicker = new ConcurrentHashMap<>();

    public IdempotentOrderConsumer(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = KafkaConfig.ORDER_EVENTS_TOPIC, groupId = "cdc-analytics-group")
    public void onMessage(String payload) {
        processMessage(payload).block();
    }

    /**
     * Idempotently processes an event payload.
     * Guaranteed safe against at-least-once duplicate delivery.
     */
    public Mono<Boolean> processMessage(String payload) {
        totalReceivedCount.incrementAndGet();

        try {
            OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
            String eventId = event.getEventId();

            String insertLogSql = """
                INSERT INTO idempotent_consumer_log (event_id, consumer_name)
                VALUES ($1, $2)
                ON CONFLICT (event_id) DO NOTHING
                """;

            return databaseClient.sql(insertLogSql)
                    .bind("$1", eventId)
                    .bind("$2", "AnalyticsOlapConsumer")
                    .fetch()
                    .rowsUpdated()
                    .map(rowsUpdated -> {
                        if (rowsUpdated > 0) {
                            // First time processing this event
                            distinctProcessedCount.incrementAndGet();
                            volumeByTicker.merge(event.getTicker(), event.getQuantity(), BigDecimal::add);
                            log.info("Processed new order event {} for ticker {} (qty: {})",
                                    eventId, event.getTicker(), event.getQuantity());
                            return true;
                        } else {
                            // Duplicate detected! Skip business logic
                            duplicateFilteredCount.incrementAndGet();
                            log.warn("Deduplicated duplicate event {}. Skipping processing.", eventId);
                            return false;
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to parse event payload: {}", payload, e);
            return Mono.error(e);
        }
    }

    public long getTotalReceivedCount() {
        return totalReceivedCount.get();
    }

    public long getDistinctProcessedCount() {
        return distinctProcessedCount.get();
    }

    public long getDuplicateFilteredCount() {
        return duplicateFilteredCount.get();
    }

    public BigDecimal getVolumeForTicker(String ticker) {
        return volumeByTicker.getOrDefault(ticker, BigDecimal.ZERO);
    }

    public void resetTelemetry() {
        totalReceivedCount.set(0);
        distinctProcessedCount.set(0);
        duplicateFilteredCount.set(0);
        volumeByTicker.clear();
    }
}
