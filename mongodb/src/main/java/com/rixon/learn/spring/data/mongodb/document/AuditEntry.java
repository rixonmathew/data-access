package com.rixon.learn.spring.data.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    private String action;
    private String performedBy;
    private String reason;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
