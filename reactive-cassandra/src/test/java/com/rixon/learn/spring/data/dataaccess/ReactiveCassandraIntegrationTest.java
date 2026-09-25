package com.rixon.learn.spring.data.dataaccess;

import com.rixon.learn.spring.data.dataaccess.model.HotMarketQuote;
import com.rixon.learn.spring.data.dataaccess.model.MarketTickKey;
import com.rixon.learn.spring.data.dataaccess.model.MarketTickRecord;
import com.rixon.learn.spring.data.dataaccess.service.ReactiveTradingStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class ReactiveCassandraIntegrationTest {

    @Container
    static CassandraContainer cassandra = new CassandraContainer("cassandra:4.1");

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) throws Exception {
        cassandra.execInContainer("cqlsh", "-e",
                "CREATE KEYSPACE IF NOT EXISTS reactive_trading_store WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }; " +
                "CREATE TABLE IF NOT EXISTS reactive_trading_store.market_ticks (" +
                "ticker text, bucket_hour text, tick_timestamp timestamp, tick_id text, " +
                "bid decimal, ask decimal, last_price decimal, volume decimal, " +
                "PRIMARY KEY ((ticker, bucket_hour), tick_timestamp, tick_id)" +
                ") WITH CLUSTERING ORDER BY (tick_timestamp DESC, tick_id ASC); " +
                "CREATE TABLE IF NOT EXISTS reactive_trading_store.hot_market_quotes (" +
                "ticker text PRIMARY KEY, bid decimal, ask decimal, last_price decimal, updated_at timestamp);"
        );

        registry.add("spring.cassandra.contact-points", () -> cassandra.getContactPoint().getHostString());
        registry.add("spring.cassandra.port", () -> cassandra.getContactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", cassandra::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> "reactive_trading_store");
    }

    @Autowired
    private ReactiveTradingStreamService tradingStreamService;

    @Test
    @DisplayName("Reactive Cassandra Scenario 1: Non-blocking Stream Ingestion & Clustering Order")
    void testReactiveTickStreamIngestionAndClusteringOrder() {
        String ticker = "NVDA";
        String bucketHour = "2026-09-21T10";
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Generate 5 ticks reactively with increasing timestamps
        Flux<MarketTickRecord> tickStream = Flux.range(1, 5)
                .map(i -> MarketTickRecord.builder()
                        .key(MarketTickKey.builder()
                                .ticker(ticker)
                                .bucketHour(bucketHour)
                                .tickTimestamp(baseTime.plusSeconds(i * 10))
                                .tickId("TICK-00" + i)
                                .build())
                        .bid(new BigDecimal("130.00").add(BigDecimal.valueOf(i)))
                        .ask(new BigDecimal("130.05").add(BigDecimal.valueOf(i)))
                        .lastPrice(new BigDecimal("130.02").add(BigDecimal.valueOf(i)))
                        .volume(BigDecimal.valueOf(100 * i))
                        .build());

        // Ingest reactively
        StepVerifier.create(tradingStreamService.ingestTickStream(tickStream))
                .expectNextCount(5)
                .verifyComplete();

        // Read stream back from Cassandra: must be pre-sorted descending by timestamp (TICK-005 down to TICK-001)
        Flux<MarketTickRecord> stream = tradingStreamService.streamTicksForHour(ticker, bucketHour);

        StepVerifier.create(stream)
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("TICK-005"))
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("TICK-004"))
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("TICK-003"))
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("TICK-002"))
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("TICK-001"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactive Cassandra Scenario 2: Reactive Time Window Slice Query")
    void testReactiveTimeWindowQuery() {
        String ticker = "MSFT";
        String bucketHour = "2026-09-21T11";
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Instant t1 = baseTime.plusSeconds(60);
        Instant t2 = baseTime.plusSeconds(120);
        Instant t3 = baseTime.plusSeconds(180);
        Instant t4 = baseTime.plusSeconds(240);

        Flux<MarketTickRecord> ticks = Flux.just(
                createTick(ticker, bucketHour, t1, "M-1"),
                createTick(ticker, bucketHour, t2, "M-2"),
                createTick(ticker, bucketHour, t3, "M-3"),
                createTick(ticker, bucketHour, t4, "M-4")
        );

        StepVerifier.create(tradingStreamService.ingestTickStream(ticks))
                .expectNextCount(4)
                .verifyComplete();

        // Window: [t2, t3]
        Flux<MarketTickRecord> windowStream = tradingStreamService.streamTicksInWindow(ticker, bucketHour, t2, t3);

        StepVerifier.create(windowStream)
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("M-3"))
                .assertNext(tick -> assertThat(tick.getKey().getTickId()).isEqualTo("M-2"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactive Cassandra Scenario 3: Native Cassandra Per-Row TTL (Time-To-Live) Expiration")
    void testEphemeralQuoteNativeCassandraTtl() {
        String ticker = "AAPL";
        HotMarketQuote quote = HotMarketQuote.builder()
                .ticker(ticker)
                .bid(new BigDecimal("150.10"))
                .ask(new BigDecimal("150.15"))
                .lastPrice(new BigDecimal("150.12"))
                .updatedAt(Instant.now())
                .build();

        // 1. Publish quote with a 2-second TTL
        StepVerifier.create(tradingStreamService.publishHotQuoteWithTtl(quote, Duration.ofSeconds(2)))
                .assertNext(saved -> assertThat(saved.getTicker()).isEqualTo("AAPL"))
                .verifyComplete();

        // 2. Immediate read: must exist
        StepVerifier.create(tradingStreamService.getHotQuote(ticker))
                .assertNext(retrieved -> {
                    assertThat(retrieved.getTicker()).isEqualTo("AAPL");
                    assertThat(retrieved.getLastPrice()).isEqualByComparingTo("150.12");
                })
                .verifyComplete();

        // 3. Wait 3 seconds for Cassandra to expire and tombstone the record
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> Boolean.TRUE.equals(
                        tradingStreamService.getHotQuote(ticker)
                                .map(q -> false)
                                .defaultIfEmpty(true)
                                .block()
                ));

        // 4. Verify Mono is empty after TTL expiration
        StepVerifier.create(tradingStreamService.getHotQuote(ticker))
                .verifyComplete();
    }

    private MarketTickRecord createTick(String ticker, String bucketHour, Instant time, String tickId) {
        return MarketTickRecord.builder()
                .key(MarketTickKey.builder()
                        .ticker(ticker)
                        .bucketHour(bucketHour)
                        .tickTimestamp(time)
                        .tickId(tickId)
                        .build())
                .bid(new BigDecimal("400.00"))
                .ask(new BigDecimal("400.10"))
                .lastPrice(new BigDecimal("400.05"))
                .volume(new BigDecimal("500"))
                .build();
    }
}
