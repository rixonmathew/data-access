package com.rixonmathew.duckdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class DuckdbApplication {

    public static void main(String[] args) {
        SpringApplication.run(DuckdbApplication.class, args);
    }

}
