package com.rixon.learn.cockroachdb.service;

import com.rixon.learn.cockroachdb.Account;
import com.rixon.learn.cockroachdb.AccountRepository;
import com.rixon.learn.cockroachdb.AccountType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CockroachAccountService {

    private static final Logger log = LoggerFactory.getLogger(CockroachAccountService.class);

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger retryAttempts = new AtomicInteger(0);

    @Transactional
    public Account createAccount(String name, AccountType type, BigDecimal balance) {
        Account account = new Account();
        account.setName(name);
        account.setType(type);
        account.setBalance(balance);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<Account> getAccount(Long id) {
        return accountRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long id) {
        return accountRepository.getBalance(id);
    }

    /**
     * Executes transfer with automatic retry logic for CockroachDB serialization conflicts (SQLState 40001).
     */
    @Retryable(
            retryFor = {ConcurrencyFailureException.class, CannotAcquireLockException.class, RuntimeException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500, random = true)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transferWithRetry(Long fromId, Long toId, BigDecimal amount) {
        int attempt = retryAttempts.incrementAndGet();
        log.info("[Attempt {}] Transferring {} from Account {} to Account {}", attempt, amount, fromId, toId);

        BigDecimal fromBalance = accountRepository.getBalanceForShare(fromId);
        if (fromBalance == null || fromBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds in account: " + fromId);
        }

        accountRepository.updateBalance(fromId, amount.negate());
        accountRepository.updateBalance(toId, amount);
    }

    @Recover
    public void recoverFromTransferConflict(Exception e, Long fromId, Long toId, BigDecimal amount) {
        log.error("Exhausted all retries for transfer from {} to {} of amount {}: {}", fromId, toId, amount, e.getMessage());
        throw new RuntimeException("CockroachDB transaction conflict retry limit exhausted", e);
    }

    /**
     * Executes CockroachDB Follower Read (AS OF SYSTEM TIME) to read historical state without blocking leaseholders.
     */
    public BigDecimal getBalanceAsOfSystemTime(Long accountId) {
        String sql = "SELECT balance FROM account AS OF SYSTEM TIME INTERVAL '-1s' WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId);
    }

    public int getRetryAttempts() {
        return retryAttempts.get();
    }

    public void resetRetryAttempts() {
        retryAttempts.set(0);
    }
}
