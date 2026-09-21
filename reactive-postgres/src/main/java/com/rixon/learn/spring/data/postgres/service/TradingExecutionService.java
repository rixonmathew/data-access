package com.rixon.learn.spring.data.postgres.service;

import com.rixon.learn.spring.data.postgres.repository.AccountReactiveRepository;
import com.rixon.learn.spring.data.postgres.repository.OrderReactiveRepository;
import com.rixon.model.account.Account;
import com.rixon.model.order.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TradingExecutionService.class);

    private final AccountReactiveRepository accountRepository;
    private final OrderReactiveRepository orderRepository;
    private final TransactionalOperator transactionalOperator;

    public Mono<Account> createAccount(Account account) {
        return accountRepository.save(account);
    }

    public Mono<Account> getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    public Mono<Account> updateAccount(Account account) {
        account.setUpdatedAt(Instant.now());
        return accountRepository.save(account);
    }

    /**
     * Executes an atomic funds transfer between two accounts within a reactive transaction.
     * If simulateFailure is true, an exception is thrown after the debit to test rollback integrity.
     */
    public Mono<Void> transferFunds(String fromAccNum, String toAccNum, BigDecimal amount, boolean simulateFailure) {
        log.info("Initiating reactive transfer of {} from {} to {} (simulateFailure={})",
                amount, fromAccNum, toAccNum, simulateFailure);

        Mono<Void> transferPipeline = Mono.zip(
                accountRepository.findByAccountNumber(fromAccNum)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Source account not found: " + fromAccNum))),
                accountRepository.findByAccountNumber(toAccNum)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Target account not found: " + toAccNum)))
        ).flatMap(tuple -> {
            Account fromAccount = tuple.getT1();
            Account toAccount = tuple.getT2();

            if (fromAccount.getBalance().compareTo(amount) < 0) {
                return Mono.error(new IllegalStateException("Insufficient balance in account: " + fromAccNum));
            }

            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            fromAccount.setUpdatedAt(Instant.now());

            toAccount.setBalance(toAccount.getBalance().add(amount));
            toAccount.setUpdatedAt(Instant.now());

            return accountRepository.save(fromAccount)
                    .flatMap(savedFrom -> {
                        if (simulateFailure) {
                            return Mono.error(new RuntimeException("Simulated transient network failure during transfer"));
                        }
                        return accountRepository.save(toAccount);
                    })
                    .then();
        });

        // Wrap pipeline in reactive transaction
        return transferPipeline.as(transactionalOperator::transactional);
    }

    public Flux<Order> saveOrders(List<Order> orders) {
        return orderRepository.saveAll(orders);
    }

    public Flux<Order> streamAllOrders(int batchSize) {
        return orderRepository.findAll().limitRate(batchSize);
    }

    public Flux<Order> getOrdersByAccount(String accountNumber) {
        return orderRepository.findByAccountNumber(accountNumber);
    }
}
