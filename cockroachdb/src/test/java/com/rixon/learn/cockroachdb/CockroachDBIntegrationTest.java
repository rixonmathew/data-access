package com.rixon.learn.cockroachdb;

import com.rixon.learn.cockroachdb.service.CockroachAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
class CockroachDBIntegrationTest {

    @Container
    static CockroachContainer cockroach = new CockroachContainer("cockroachdb/cockroach:v23.2.0");

    @DynamicPropertySource
    static void cockroachProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", cockroach::getJdbcUrl);
        registry.add("spring.datasource.username", cockroach::getUsername);
        registry.add("spring.datasource.password", cockroach::getPassword);
    }

    @Autowired
    private CockroachAccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void cleanDb() {
        accountService.resetRetryAttempts();
    }

    @Test
    @DisplayName("CockroachDB Scenario 1: Distributed Serializable Isolation & Concurrent Transfer Retries")
    void testConcurrentFundsTransferUnderDistributedContention() throws Exception {
        Account accA = accountService.createAccount("Alpha Fund", AccountType.asset, new BigDecimal("10000.00"));
        Account accB = accountService.createAccount("Beta Trust", AccountType.asset, new BigDecimal("5000.00"));

        int threadCount = 10;
        BigDecimal transferPerThread = new BigDecimal("50.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                accountService.transferWithRetry(accA.getId(), accB.getId(), transferPerThread);
                return null;
            }));
        }

        // Wait for all concurrent transfers to complete
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Verify balances after concurrent transfer contention
        BigDecimal expectedA = new BigDecimal("10000.00").subtract(transferPerThread.multiply(BigDecimal.valueOf(threadCount)));
        BigDecimal expectedB = new BigDecimal("5000.00").add(transferPerThread.multiply(BigDecimal.valueOf(threadCount)));

        BigDecimal finalA = accountService.getBalance(accA.getId());
        BigDecimal finalB = accountService.getBalance(accB.getId());

        assertThat(finalA).isEqualByComparingTo(expectedA);
        assertThat(finalB).isEqualByComparingTo(expectedB);

        // Verify invariant: Total balance is preserved across distributed transactions
        assertThat(finalA.add(finalB)).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("CockroachDB Scenario 2: Follower Read (AS OF SYSTEM TIME)")
    void testCockroachFollowerRead() throws InterruptedException {
        Account account = accountService.createAccount("Delta Follower", AccountType.asset, new BigDecimal("2500.00"));

        // Allow MVCC historical window to elapse
        Thread.sleep(1500);

        BigDecimal historicalBalance = accountService.getBalanceAsOfSystemTime(account.getId());
        assertThat(historicalBalance).isEqualByComparingTo("2500.00");
    }
}
