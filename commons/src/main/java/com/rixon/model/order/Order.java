package com.rixon.model.order;

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
@jakarta.persistence.Table(name = "orders")
@Table("orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.springframework.data.annotation.Id
    @jakarta.persistence.Column(name = "id")
    @org.springframework.data.relational.core.mapping.Column("id")
    private Long id;

    @jakarta.persistence.Column(name = "order_id", unique = true, nullable = false, length = 64)
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

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "order_type", nullable = false, length = 16)
    @org.springframework.data.relational.core.mapping.Column("order_type")
    private OrderType orderType;

    @jakarta.persistence.Column(name = "price", precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("price")
    private BigDecimal price;

    @jakarta.persistence.Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("quantity")
    private BigDecimal quantity;

    @jakarta.persistence.Column(name = "filled_quantity", precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("filled_quantity")
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "status", nullable = false, length = 32)
    @org.springframework.data.relational.core.mapping.Column("status")
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @jakarta.persistence.Column(name = "created_at")
    @org.springframework.data.relational.core.mapping.Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @jakarta.persistence.Column(name = "updated_at")
    @org.springframework.data.relational.core.mapping.Column("updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
