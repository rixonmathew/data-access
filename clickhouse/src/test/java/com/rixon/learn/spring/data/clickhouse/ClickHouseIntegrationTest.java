package com.rixon.learn.spring.data.clickhouse;

import com.rixon.learn.spring.data.clickhouse.dto.MarketTickRow;
import com.rixon.learn.spring.data.clickhouse.dto.OhlcvBarDto;
import com.rixon.learn.spring.data.clickhouse.dto.TickerMetricsDto;
import com.rixon.learn.spring.data.clickhouse.service.ClickHouseAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class ClickHouseIntegrationTest {

    @Container
    static ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server:24.3-alpine");

    @DynamicPropertySource
    static void clickhouseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", clickhouse::getJdbcUrl);
        registry.add("spring.datasource.username", clickhouse::getUsername);
        registry.add("spring.datasource.password", clickhouse::getPassword);
        registry.add("spring.datasource.driver-class-name", clickhouse::getDriverClassName);
    }

    @Autowired
    private ClickHouseAnalyticsService analyticsService;

    @BeforeEach
    void setup() {
        analyticsService.initSchema();
        analyticsService.truncateTicks();
    }

    @Test
    @DisplayName("ClickHouse Scenario 1: High-Throughput Batch Ingestion into Columnar MergeTree")
    void testBatchIngestionAndMergeTree() {
        List<MarketTickRow> batch = new ArrayList<>();
        Instant base = Instant.parse("2026-09-21T10:00:00Z");

        for (int i = 0; i < 50; i++) {
            batch.add(MarketTickRow.builder()
                    .ticker("NVDA")
                    .eventTime(Timestamp.from(base.plusSeconds(i)))
                    .bid(130.0 + (i * 0.05))
                    .ask(130.05 + (i * 0.05))
                    .lastPrice(130.02 + (i * 0.05))
                    .volume(100L + (i * 10))
                    .latencyMicros(250 + (i * 10))
                    .build());
        }

        for (int i = 0; i < 50; i++) {
            batch.add(MarketTickRow.builder()
                    .ticker("AAPL")
                    .eventTime(Timestamp.from(base.plusSeconds(i)))
                    .bid(220.0 + (i * 0.02))
                    .ask(220.04 + (i * 0.02))
                    .lastPrice(220.01 + (i * 0.02))
                    .volume(200L + (i * 5))
                    .latencyMicros(180 + (i * 5))
                    .build());
        }

        int[][] inserted = analyticsService.batchInsertTicks(batch);
        assertThat(inserted).isNotEmpty();

        List<TickerMetricsDto> metrics = analyticsService.calculateTickerMetrics();
        assertThat(metrics).hasSize(2);
        assertThat(metrics.get(0).getTicker()).isEqualTo("AAPL");
        assertThat(metrics.get(0).getTickCount()).isEqualTo(50);
        assertThat(metrics.get(1).getTicker()).isEqualTo("NVDA");
        assertThat(metrics.get(1).getTickCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("ClickHouse Scenario 2: Columnar Quantiles & Statistical Metric Rollups")
    void testColumnarQuantilesAndRollups() {
        List<MarketTickRow> batch = new ArrayList<>();
        Instant base = Instant.parse("2026-09-21T10:00:00Z");

        // Latencies ranging from 100 to 1000 micros
        for (int i = 1; i <= 100; i++) {
            batch.add(MarketTickRow.builder()
                    .ticker("MSFT")
                    .eventTime(Timestamp.from(base.plus(i * 100, ChronoUnit.MILLIS)))
                    .bid(400.0)
                    .ask(400.10)
                    .lastPrice(400.05)
                    .volume(50L)
                    .latencyMicros(i * 10) // 10, 20, ..., 1000 micros
                    .build());
        }

        analyticsService.batchInsertTicks(batch);

        List<TickerMetricsDto> metrics = analyticsService.calculateTickerMetrics();
        assertThat(metrics).hasSize(1);

        TickerMetricsDto msft = metrics.get(0);
        assertThat(msft.getTicker()).isEqualTo("MSFT");
        assertThat(msft.getTickCount()).isEqualTo(100);
        assertThat(msft.getAvgPrice()).isCloseTo(400.05, within(0.001));
        assertThat(msft.getTotalVolume()).isEqualTo(5000L);

        // Quantiles: p50 should be ~500, p95 ~950, p99 ~990
        assertThat(msft.getP50Latency()).isCloseTo(500.0, within(25.0));
        assertThat(msft.getP95Latency()).isCloseTo(950.0, within(25.0));
        assertThat(msft.getP99Latency()).isCloseTo(990.0, within(25.0));
        assertThat(msft.getP50Latency()).isLessThan(msft.getP95Latency());
        assertThat(msft.getP95Latency()).isLessThan(msft.getP99Latency());
    }

    @Test
    @DisplayName("ClickHouse Scenario 3: Real-Time Candlestick (OHLCV) Aggregation using argMin/argMax")
    void testOhlcvCandlestickAggregation() {
        Instant minute1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant minute2 = Instant.parse("2026-09-21T10:01:00Z");

        List<MarketTickRow> ticks = List.of(
                // Minute 1 ticks
                createTick("NVDA", minute1.plusSeconds(5), 130.00, 100L),  // Open = 130.00
                createTick("NVDA", minute1.plusSeconds(15), 135.50, 200L), // High = 135.50
                createTick("NVDA", minute1.plusSeconds(30), 128.00, 150L), // Low  = 128.00
                createTick("NVDA", minute1.plusSeconds(45), 132.50, 300L), // Close = 132.50

                // Minute 2 ticks
                createTick("NVDA", minute2.plusSeconds(10), 133.00, 100L), // Open = 133.00
                createTick("NVDA", minute2.plusSeconds(25), 138.00, 500L), // High = 138.00
                createTick("NVDA", minute2.plusSeconds(40), 131.00, 200L), // Low  = 131.00
                createTick("NVDA", minute2.plusSeconds(55), 136.00, 400L)  // Close = 136.00
        );

        analyticsService.batchInsertTicks(ticks);

        List<OhlcvBarDto> bars = analyticsService.calculateOhlcvBars("NVDA", 1);
        assertThat(bars).hasSize(2);

        // Validate Bar 1
        OhlcvBarDto bar1 = bars.get(0);
        assertThat(bar1.getTicker()).isEqualTo("NVDA");
        assertThat(bar1.getOpen()).isCloseTo(130.00, within(0.001));
        assertThat(bar1.getHigh()).isCloseTo(135.50, within(0.001));
        assertThat(bar1.getLow()).isCloseTo(128.00, within(0.001));
        assertThat(bar1.getClose()).isCloseTo(132.50, within(0.001));
        assertThat(bar1.getVolume()).isEqualTo(750L);

        // Validate Bar 2
        OhlcvBarDto bar2 = bars.get(1);
        assertThat(bar2.getTicker()).isEqualTo("NVDA");
        assertThat(bar2.getOpen()).isCloseTo(133.00, within(0.001));
        assertThat(bar2.getHigh()).isCloseTo(138.00, within(0.001));
        assertThat(bar2.getLow()).isCloseTo(131.00, within(0.001));
        assertThat(bar2.getClose()).isCloseTo(136.00, within(0.001));
        assertThat(bar2.getVolume()).isEqualTo(1200L);
    }

    private MarketTickRow createTick(String ticker, Instant time, double price, long volume) {
        return MarketTickRow.builder()
                .ticker(ticker)
                .eventTime(Timestamp.from(time))
                .bid(price - 0.05)
                .ask(price + 0.05)
                .lastPrice(price)
                .volume(volume)
                .latencyMicros(200)
                .build();
    }
}
