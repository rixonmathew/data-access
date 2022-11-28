package com.rixon.model.contract;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;


@Entity
@Data
public class Contract {
    @Id
    private String id;
    private String type;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private String assetIdentifier;
    private String assetIdentifierType;
    private BigDecimal quantity;
    private String comments;
}
