package com.rixon.learn.spring.data.cdc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status; // PENDING, PUBLISHED, FAILED
    private int retryCount;
    private Instant createdAt;
    private Instant publishedAt;
}
