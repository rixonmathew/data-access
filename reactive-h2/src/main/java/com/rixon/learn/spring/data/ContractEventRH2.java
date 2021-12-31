package com.rixon.learn.spring.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ContractEventRH2 implements Persistable<String> {

    @Id
    private String id;
    private String contractId;
    private LocalDate eventDate;
    private String type;
    private BigDecimal economicChange;
    private Integer quantity;

    @Override
    public boolean isNew() {
        return true;
    }
}
