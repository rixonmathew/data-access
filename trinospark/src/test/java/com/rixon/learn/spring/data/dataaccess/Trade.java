package com.rixon.learn.spring.data.dataaccess;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Trade {
    private String id;
    private String account;
    private String ticket;
    private LocalDate tradeDate;
    private BigDecimal value;
}
