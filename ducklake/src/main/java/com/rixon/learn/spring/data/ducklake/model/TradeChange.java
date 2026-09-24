package com.rixon.learn.spring.data.ducklake.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One row of the DuckLake change feed ({@code table_changes}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeChange {
    private long snapshotId;
    /** insert, delete, update_preimage or update_postimage. */
    private String changeType;
    private String tradeId;
    private BigDecimal price;
}
