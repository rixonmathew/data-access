package com.rixon.learn.spring.data.dataaccess;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Trade(
    String id,
    String account,
    String ticker,
    LocalDate tradeDate,
    BigDecimal value
){}
