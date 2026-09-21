package com.rixon.learn.spring.data.dataaccess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;

@Table("trade_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeExecutionRecord {

    @PrimaryKey
    private TradeExecutionKey key;

    @Column("order_id")
    private String orderId;

    @Column("account_number")
    private String accountNumber;

    @Column("price")
    private BigDecimal price;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("liquidity_indicator")
    private String liquidityIndicator;

    @Column("venue")
    private String venue;
}
