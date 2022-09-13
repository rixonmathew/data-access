package com.rixon.learn.spring.data.dataaccess;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Trade {
    private String id;
    private String account;
    private String ticker;
    private LocalDate tradeDate;
    private BigDecimal value;
}
