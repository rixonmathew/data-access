package com.rixon.learn.spring.data.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
@CompoundIndex(name = "ticker_side_idx", def = "{'ticker': 1, 'side': 1}")
public class OrderDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    @Indexed
    private String accountNumber;

    private String ticker;

    private String side;

    private String orderType;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal filledQuantity;

    private String status;

    private ExecutionStrategy strategy;

    @Builder.Default
    private List<AuditEntry> auditTrail = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> customAttributes = new HashMap<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void addAuditEntry(String action, String performedBy, String reason) {
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
        auditTrail.add(AuditEntry.builder()
                .action(action)
                .performedBy(performedBy)
                .reason(reason)
                .timestamp(Instant.now())
                .build());
    }
}
