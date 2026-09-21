package com.rixon.learn.spring.data.postgres;

import com.rixon.learn.spring.data.postgres.repository.AccountReactiveRepository;
import com.rixon.learn.spring.data.postgres.repository.OrderReactiveRepository;
import com.rixon.learn.spring.data.postgres.service.TradingExecutionService;
import com.rixon.model.account.Account;
import com.rixon.model.account.AccountStatus;
import com.rixon.model.account.AccountType;
import com.rixon.model.order.Order;
import com.rixon.model.util.DataGeneratorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIfDockerAvailable
public class ReactiveTradingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TradingExecutionService tradingService;

    @Autowired
    private AccountReactiveRepository accountRepository;

    @Autowired
    private OrderReactiveRepository orderRepository;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        registry.add("spring.r2dbc.host", postgres::getHost);
        registry.add("spring.r2dbc.port", postgres::getFirstMappedPort);
        registry.add("spring.r2dbc.database", postgres::getDatabaseName);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll().block();
        accountRepository.deleteAll().block();
    }

    @Test
    @DisplayName("Scenario 1: Atomic Funds Transfer - Happy Path")
    void testAtomicFundsTransferSuccess() {
        Account acc1 = Account.builder()
                .accountNumber("ACC-PG-001")
                .holderName("Alice Trader")
                .email("alice@trading.com")
                .type(AccountType.CASH)
                .balance(new BigDecimal("1000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        Account acc2 = Account.builder()
                .accountNumber("ACC-PG-002")
                .holderName("Bob Broker")
                .email("bob@trading.com")
                .type(AccountType.MARGIN)
                .balance(new BigDecimal("500.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.saveAll(List.of(acc1, acc2)).collectList().block();

        // Transfer 300.00 from Alice to Bob
        tradingService.transferFunds("ACC-PG-001", "ACC-PG-002", new BigDecimal("300.0000"), false)
                .as(StepVerifier::create)
                .verifyComplete();

        // Verify Alice balance = 700.00
        accountRepository.findByAccountNumber("ACC-PG-001")
                .as(StepVerifier::create)
                .assertNext(acc -> assertThat(acc.getBalance()).isEqualByComparingTo("700.0000"))
                .verifyComplete();

        // Verify Bob balance = 800.00
        accountRepository.findByAccountNumber("ACC-PG-002")
                .as(StepVerifier::create)
                .assertNext(acc -> assertThat(acc.getBalance()).isEqualByComparingTo("800.0000"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Scenario 2: Atomic Funds Transfer - Rollback Integrity on Simulated Failure")
    void testAtomicFundsTransferRollbackOnFailure() {
        Account acc1 = Account.builder()
                .accountNumber("ACC-PG-003")
                .holderName("Charlie Alpha")
                .email("charlie@trading.com")
                .type(AccountType.CASH)
                .balance(new BigDecimal("2000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        Account acc2 = Account.builder()
                .accountNumber("ACC-PG-004")
                .holderName("Diana Beta")
                .email("diana@trading.com")
                .type(AccountType.CASH)
                .balance(new BigDecimal("1000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.saveAll(List.of(acc1, acc2)).collectList().block();

        // Attempt transfer with simulateFailure = true
        tradingService.transferFunds("ACC-PG-003", "ACC-PG-004", new BigDecimal("500.0000"), true)
                .as(StepVerifier::create)
                .expectErrorMatches(t -> t.getMessage().contains("Simulated transient network failure"))
                .verify();

        // CRITICAL: Verify Charlie's balance was NOT deducted (complete rollback)
        accountRepository.findByAccountNumber("ACC-PG-003")
                .as(StepVerifier::create)
                .assertNext(acc -> assertThat(acc.getBalance()).isEqualByComparingTo("2000.0000"))
                .verifyComplete();

        // CRITICAL: Verify Diana's balance was NOT credited
        accountRepository.findByAccountNumber("ACC-PG-004")
                .as(StepVerifier::create)
                .assertNext(acc -> assertThat(acc.getBalance()).isEqualByComparingTo("1000.0000"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Scenario 3: Optimistic Locking Conflict Detection (@Version)")
    void testOptimisticLockingConflictDetection() {
        Account initial = Account.builder()
                .accountNumber("ACC-PG-005")
                .holderName("Eve Investor")
                .email("eve@trading.com")
                .type(AccountType.INSTITUTIONAL)
                .balance(new BigDecimal("50000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(initial).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        Long initialVersion = saved.getVersion();

        // Worker 1 and Worker 2 fetch the same account concurrently
        Account worker1Copy = accountRepository.findByAccountNumber("ACC-PG-005").block();
        Account worker2Copy = accountRepository.findByAccountNumber("ACC-PG-005").block();

        assertThat(worker1Copy).isNotNull();
        assertThat(worker2Copy).isNotNull();

        // Worker 1 updates balance and saves successfully (version increments)
        worker1Copy.setBalance(new BigDecimal("60000.0000"));
        Account updatedByWorker1 = accountRepository.save(worker1Copy).block();
        assertThat(updatedByWorker1).isNotNull();
        assertThat(updatedByWorker1.getVersion()).isEqualTo(initialVersion + 1);

        // Worker 2 attempts to save its stale copy (which still has initial version)
        worker2Copy.setBalance(new BigDecimal("40000.0000"));

        accountRepository.save(worker2Copy)
                .as(StepVerifier::create)
                .expectError(OptimisticLockingFailureException.class)
                .verify();

        // Verify the database contains Worker 1's value and did not get overwritten by Worker 2
        accountRepository.findByAccountNumber("ACC-PG-005")
                .as(StepVerifier::create)
                .assertNext(acc -> {
                    assertThat(acc.getBalance()).isEqualByComparingTo("60000.0000");
                    assertThat(acc.getVersion()).isEqualTo(initialVersion + 1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Scenario 4: High-Throughput Streaming with Reactive Backpressure")
    void testStreamingOrdersWithBackpressure() {
        // Insert 100 random orders
        List<Order> generatedOrders = DataGeneratorUtils.randomOrders(100);
        tradingService.saveOrders(generatedOrders).collectList().block();

        // Stream all orders with a limitRate of 25 (backpressure chunks)
        StepVerifier.create(tradingService.streamAllOrders(25), 0)
                .thenRequest(25)
                .expectNextCount(25)
                .thenRequest(25)
                .expectNextCount(25)
                .thenRequest(50)
                .expectNextCount(50)
                .verifyComplete();
    }
}
