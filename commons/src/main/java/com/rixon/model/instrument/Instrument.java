package com.rixon.model.instrument;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "INSTRUMENT")
@Table("INSTRUMENT")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.springframework.data.annotation.Id
    @Column("ID")
    @jakarta.persistence.Column(name = "ID")
    private Long id;

    @Column("TYPE")
    @jakarta.persistence.Column(name = "TYPE")
    private String type;

    @Column("NAME")
    @jakarta.persistence.Column(name = "NAME")
    private String name;

    @org.springframework.data.annotation.Transient
    @jakarta.persistence.Transient
    private String ticker;

    @org.springframework.data.annotation.Transient
    @jakarta.persistence.Transient
    private String isin;

    @org.springframework.data.annotation.Transient
    @jakarta.persistence.Transient
    private AssetClass assetClass;

    @org.springframework.data.annotation.Transient
    @jakarta.persistence.Transient
    @Builder.Default
    private String currency = "USD";

    @org.springframework.data.annotation.Transient
    @jakarta.persistence.Transient
    private BigDecimal lastPrice;

    @Column("METADATA")
    @jakarta.persistence.Column(name = "METADATA")
    private String metadata;
}
