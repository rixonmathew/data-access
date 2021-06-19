package com.rixon.learn.spring.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ContractEventRH2 implements Persistable<String> {

    @Id
    private String eventId;
    private String contractId;
    private Date eventDate;
    private String type;
    private BigDecimal economicChange;
    private Integer quantity;

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
