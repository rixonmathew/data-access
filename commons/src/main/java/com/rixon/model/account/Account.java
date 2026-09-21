package com.rixon.model.account;

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
@jakarta.persistence.Table(name = "accounts")
@Table("accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.springframework.data.annotation.Id
    @jakarta.persistence.Column(name = "id")
    @org.springframework.data.relational.core.mapping.Column("id")
    private Long id;

    @jakarta.persistence.Column(name = "account_number", unique = true, nullable = false, length = 64)
    @org.springframework.data.relational.core.mapping.Column("account_number")
    private String accountNumber;

    @jakarta.persistence.Column(name = "holder_name", nullable = false, length = 128)
    @org.springframework.data.relational.core.mapping.Column("holder_name")
    private String holderName;

    @jakarta.persistence.Column(name = "email", length = 128)
    @org.springframework.data.relational.core.mapping.Column("email")
    private String email;

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "account_type", nullable = false, length = 32)
    @org.springframework.data.relational.core.mapping.Column("account_type")
    private AccountType type;

    @jakarta.persistence.Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @org.springframework.data.relational.core.mapping.Column("balance")
    private BigDecimal balance;

    @jakarta.persistence.Column(name = "currency", nullable = false, length = 3)
    @org.springframework.data.relational.core.mapping.Column("currency")
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "status", nullable = false, length = 32)
    @org.springframework.data.relational.core.mapping.Column("status")
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @jakarta.persistence.Version
    @org.springframework.data.annotation.Version
    @jakarta.persistence.Column(name = "version")
    @org.springframework.data.relational.core.mapping.Column("version")
    private Long version;

    @jakarta.persistence.Column(name = "created_at")
    @org.springframework.data.relational.core.mapping.Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @jakarta.persistence.Column(name = "updated_at")
    @org.springframework.data.relational.core.mapping.Column("updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
