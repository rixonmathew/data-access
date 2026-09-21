package com.rixon.learn.spring.data.trino.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@Configuration
public class TrinoConfig {

    @Value("${spring.datasource.url:jdbc:trino://localhost:8080}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:test}")
    private String username;

    @Value("${spring.datasource.driver-class-name:io.trino.jdbc.TrinoDriver}")
    private String driverClassName;

    @Bean
    public DataSource trinoDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword("");
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
