package com.rixon.learn.spring.data.dataaccess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;

@Table("market_ticks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTickRecord {

    @PrimaryKey
    private MarketTickKey key;

    @Column("bid")
    private BigDecimal bid;

    @Column("ask")
    private BigDecimal ask;

    @Column("last_price")
    private BigDecimal lastPrice;

    @Column("volume")
    private BigDecimal volume;
}
