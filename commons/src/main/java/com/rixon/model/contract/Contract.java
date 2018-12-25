package com.rixon.model.contract;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;


@AllArgsConstructor
@ToString
@EqualsAndHashCode
@NoArgsConstructor
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
