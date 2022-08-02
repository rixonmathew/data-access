package com.rixon.learn.spring.data.dataaccess;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Employee {
    private String name;
    private String dept;
    private BigDecimal salary;
}
