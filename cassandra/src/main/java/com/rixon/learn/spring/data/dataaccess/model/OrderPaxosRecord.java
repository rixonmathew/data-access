package com.rixon.learn.spring.data.dataaccess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;

@Table("orders_paxos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaxosRecord {

    @PrimaryKey("order_id")
    private String orderId;

    @Column("account_number")
    private String accountNumber;

    @Column("ticker")
    private String ticker;

    @Column("side")
    private String side;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("price")
    private BigDecimal price;

    @Column("status")
    private String status;

    @Column("version")
    private Long version;
}
