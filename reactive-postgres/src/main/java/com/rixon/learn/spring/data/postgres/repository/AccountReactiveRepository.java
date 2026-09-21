package com.rixon.learn.spring.data.postgres.repository;

import com.rixon.model.account.Account;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public interface AccountReactiveRepository extends R2dbcRepository<Account, Long> {

    Mono<Account> findByAccountNumber(String accountNumber);

    Flux<Account> findByBalanceGreaterThanEqual(BigDecimal threshold);

    @Modifying
    @Query("UPDATE accounts SET balance = balance + :amount, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE account_number = :accountNumber")
    Mono<Integer> adjustBalance(String accountNumber, BigDecimal amount);
}
