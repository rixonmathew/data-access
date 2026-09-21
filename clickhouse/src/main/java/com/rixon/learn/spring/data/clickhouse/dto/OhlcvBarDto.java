package com.rixon.learn.spring.data.clickhouse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OhlcvBarDto {
    private String ticker;
    private Timestamp barTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
