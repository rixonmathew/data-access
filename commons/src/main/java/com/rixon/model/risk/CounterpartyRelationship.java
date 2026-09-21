package com.rixon.model.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyRelationship {
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private RelationshipType relationshipType;
    private BigDecimal exposureLimit;
    private BigDecimal currentExposure;
    @Builder.Default
    private Instant effectiveDate = Instant.now();
}
