package com.rixon.learn.spring.data.cdc.service;

import com.rixon.learn.spring.data.cdc.config.KafkaConfig;
import com.rixon.learn.spring.data.cdc.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final DatabaseClient databaseClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Polls pending outbox records using FOR UPDATE SKIP LOCKED,
     * dispatches them to Redpanda/Kafka, and updates their status to PUBLISHED.
     */
    public Flux<OutboxEvent> relayPendingEvents() {
        String selectPendingSql = """
            SELECT event_id, aggregate_type, aggregate_id, event_type, payload::text as payload_text, status
            FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            """;

        return databaseClient.sql(selectPendingSql)
                .map((row, md) -> OutboxEvent.builder()
                        .eventId(row.get("event_id", String.class))
                        .aggregateType(row.get("aggregate_type", String.class))
                        .aggregateId(row.get("aggregate_id", String.class))
                        .eventType(row.get("event_type", String.class))
                        .payload(row.get("payload_text", String.class))
                        .status(row.get("status", String.class))
                        .build())
                .all()
                .concatMap(this::publishAndMarkPublished);
    }

    private Mono<OutboxEvent> publishAndMarkPublished(OutboxEvent event) {
        log.info("Relaying Outbox event {} for aggregate {} to topic {}",
                event.getEventId(), event.getAggregateId(), KafkaConfig.ORDER_EVENTS_TOPIC);

        return Mono.fromFuture(kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getAggregateId(), event.getPayload()))
                .flatMap(sendResult -> {
                    String updateSql = """
                        UPDATE outbox_events
                        SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP
                        WHERE event_id = $1
                        """;

                    return databaseClient.sql(updateSql)
                            .bind("$1", event.getEventId())
                            .then()
                            .thenReturn(OutboxEvent.builder()
                                    .eventId(event.getEventId())
                                    .aggregateType(event.getAggregateType())
                                    .aggregateId(event.getAggregateId())
                                    .eventType(event.getEventType())
                                    .payload(event.getPayload())
                                    .status("PUBLISHED")
                                    .publishedAt(Instant.now())
                                    .build());
                });
    }
}
