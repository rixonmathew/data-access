package com.rixon.model.trade;

import com.rixon.model.order.OrderSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "trade_executions")
@Table("trade_executions")
public class TradeExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.springframework.data.annotation.Id
    @jakarta.persistence.Column(name = "id")
    @org.springframework.data.relational.core.mapping.Column("id")
    private Long id;

    @jakarta.persistence.Column(name = "trade_id", unique = true, nullable = false, length = 64)
    @org.springframework.data.relational.core.mapping.Column("trade_id")
    private String tradeId;

    @jakarta.persistence.Column(name = "order_id", nullable = false, length = 64)
    @org.springframework.data.relational.core.mapping.Column("order_id")
    private String orderId;

    @jakarta.persistence.Column(name = "account_number", nullable = false, length = 64)
    @org.springframework.data.relational.core.mapping.Column("account_number")
    private String accountNumber;

    @jakarta.persistence.Column(name = "ticker", nullable = false, length = 16)
    @org.springframework.data.relational.core.mapping.Column("ticker")
    private String ticker;

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "side", nullable = false, length = 16)
    @org.springframework.data.relational.core.mapping.Column("side")
    private OrderSide side;

    @jakarta.persistence.Column(name = "executed_price", nullable = false, precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("executed_price")
    private BigDecimal executedPrice;

    @jakarta.persistence.Column(name = "executed_quantity", nullable = false, precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("executed_quantity")
    private BigDecimal executedQuantity;

    @jakarta.persistence.Column(name = "execution_time", nullable = false)
    @org.springframework.data.relational.core.mapping.Column("execution_time")
    @Builder.Default
    private Instant executionTime = Instant.now();

    @jakarta.persistence.Column(name = "exchange", length = 32)
    @org.springframework.data.relational.core.mapping.Column("exchange")
    @Builder.Default
    private String exchange = "NASDAQ";

    @jakarta.persistence.Column(name = "counterparty_account", length = 64)
    @org.springframework.data.relational.core.mapping.Column("counterparty_account")
    private String counterpartyAccountNumber;
}
