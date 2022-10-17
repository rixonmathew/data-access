package com.rixon.learn.spring.data.dataaccess;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Order {
    private String orderId;
    private String customerId;
    private LocalDate orderDate;
    private String state;
    private BigDecimal value;
    private String status;
}
