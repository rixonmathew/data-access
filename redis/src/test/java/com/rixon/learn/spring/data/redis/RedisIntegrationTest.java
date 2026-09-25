package com.rixon.learn.spring.data.redis;

import com.rixon.learn.spring.data.redis.service.RedisCacheAsideService;
import com.rixon.learn.spring.data.redis.service.RedisDistributedLockService;
import com.rixon.learn.spring.data.redis.service.RedisMarketQuoteStreamService;
import com.rixon.learn.spring.data.redis.service.RedisRateLimiterService;
import com.rixon.model.account.Account;
import com.rixon.model.account.AccountStatus;
import com.rixon.model.account.AccountType;
import com.rixon.model.market.MarketQuote;
import com.rixon.model.util.DataGeneratorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RedisDistributedLockService lockService;

    @Autowired
    private RedisRateLimiterService rateLimiterService;

    @Autowired
    private RedisCacheAsideService cacheAsideService;

    @Autowired
    private RedisMarketQuoteStreamService streamService;

    @Test
    @DisplayName("Redis Scenario 1: Distributed Lock Mutual Exclusion & Safe Release")
    void testDistributedLockingMutualExclusion() {
        String lockKey = "lock:account:ACC-REDIS-001";
        String worker1Token = UUID.randomUUID().toString();
        String worker2Token = UUID.randomUUID().toString();

        // Worker 1 acquires lock
        boolean acquired1 = lockService.acquireLock(lockKey, worker1Token, 5000);
        assertThat(acquired1).isTrue();

        // Worker 2 attempts to acquire same lock -> must fail
        boolean acquired2 = lockService.acquireLock(lockKey, worker2Token, 5000);
        assertThat(acquired2).isFalse();

        // Worker 2 attempts to maliciously or accidentally release Worker 1's lock -> must fail (Lua script check)
        boolean releasedByWorker2 = lockService.releaseLock(lockKey, worker2Token);
        assertThat(releasedByWorker2).isFalse();

        // Worker 1 releases lock safely
        boolean releasedByWorker1 = lockService.releaseLock(lockKey, worker1Token);
        assertThat(releasedByWorker1).isTrue();

        // Worker 2 can now acquire lock
        boolean acquiredAfterRelease = lockService.acquireLock(lockKey, worker2Token, 5000);
        assertThat(acquiredAfterRelease).isTrue();
        lockService.releaseLock(lockKey, worker2Token);
    }

    @Test
    @DisplayName("Redis Scenario 2: Sliding Window Rate Limiter")
    void testSlidingWindowRateLimiter() throws InterruptedException {
        String clientKey = "client:trader_99";
        int maxRequests = 5;
        long windowSeconds = 2;

        // First 5 requests must be permitted
        for (int i = 0; i < maxRequests; i++) {
            assertThat(rateLimiterService.isAllowed(clientKey, maxRequests, windowSeconds))
                    .isTrue();
        }

        // 6th request within window must be rejected
        assertThat(rateLimiterService.isAllowed(clientKey, maxRequests, windowSeconds))
                .isFalse();

        // Wait for sliding window to elapse
        Thread.sleep(2100);

        // Subsequent request must be permitted again
        assertThat(rateLimiterService.isAllowed(clientKey, maxRequests, windowSeconds))
                .isTrue();
    }

    @Test
    @DisplayName("Redis Scenario 3: Cache-Aside with Automatic Expiration & Eviction")
    void testCacheAsideWithEviction() {
        String accNum = "ACC-REDIS-CACHE-01";
        AtomicInteger dbHits = new AtomicInteger(0);

        Account dbAccount = Account.builder()
                .accountNumber(accNum)
                .holderName("Sarah Connor")
                .email("sarah@resistance.org")
                .type(AccountType.CASH)
                .balance(new BigDecimal("99999.0000"))
                .status(AccountStatus.ACTIVE)
                .build();

        // First call: Cache miss -> invokes DB supplier
        Account acc1 = cacheAsideService.getAccount(accNum, () -> {
            dbHits.incrementAndGet();
            return dbAccount;
        }, Duration.ofMinutes(5));

        assertThat(acc1).isNotNull();
        assertThat(acc1.getAccountNumber()).isEqualTo(accNum);
        assertThat(dbHits.get()).isEqualTo(1);

        // Second call: Cache hit -> does NOT invoke DB supplier
        Account acc2 = cacheAsideService.getAccount(accNum, () -> {
            dbHits.incrementAndGet();
            return dbAccount;
        }, Duration.ofMinutes(5));

        assertThat(acc2).isNotNull();
        assertThat(dbHits.get()).isEqualTo(1); // Unchanged!

        // Evict cache
        cacheAsideService.evictAccount(accNum);

        // Third call after eviction: Cache miss -> invokes DB supplier again
        Account acc3 = cacheAsideService.getAccount(accNum, () -> {
            dbHits.incrementAndGet();
            return dbAccount;
        }, Duration.ofMinutes(5));

        assertThat(acc3).isNotNull();
        assertThat(dbHits.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis Scenario 4: Real-time Market Quote Event Stream")
    void testMarketQuoteStreamPublishAndRead() {
        List<MarketQuote> quotes = DataGeneratorUtils.randomMarketQuotes("NVDA", 5);

        // Publish quotes to Redis Stream
        for (MarketQuote quote : quotes) {
            streamService.publishQuote(quote);
        }

        // Read latest quotes back from Stream
        List<MarketQuote> streamResults = streamService.readLatestQuotes(5);

        assertThat(streamResults).isNotEmpty();
        assertThat(streamResults).hasSize(5);
        for (MarketQuote q : streamResults) {
            assertThat(q.ticker()).isEqualTo("NVDA");
            assertThat(q.lastPrice()).isNotNull();
            assertThat(q.volume()).isGreaterThan(0);
        }
    }
}
