package com.rixon.learn.spring.data.dataaccess;

import org.springframework.data.jdbc.repository.config.DialectResolver;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.H2Dialect;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TrinoDialectProvider implements DialectResolver.JdbcDialectProvider {
    @Override
    public Optional<Dialect> getDialect(JdbcOperations operations) {
        return Optional.of(H2Dialect.INSTANCE);
    }
}
