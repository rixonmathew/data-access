package com.rixon.learn.spring.data.dataaccess.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(collection = "contracts")
@Data
public class ContractMongo {

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
