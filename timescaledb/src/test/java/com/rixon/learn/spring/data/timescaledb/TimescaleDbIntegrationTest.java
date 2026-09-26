package com.rixon.learn.spring.data.timescaledb;

import com.rixon.learn.spring.data.timescaledb.model.TimescaleCandle;
import com.rixon.learn.spring.data.timescaledb.model.TimescaleCompressionStat;
import com.rixon.learn.spring.data.timescaledb.model.TimescaleTick;
import com.rixon.learn.spring.data.timescaledb.service.TimescaleDbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class TimescaleDbIntegrationTest {

    @Container
    static PostgreSQLContainer<?> timescaleContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", timescaleContainer::getJdbcUrl);
        registry.add("spring.datasource.username", timescaleContainer::getUsername);
        registry.add("spring.datasource.password", timescaleContainer::getPassword);
    }

    @Autowired
    private TimescaleDbService timescaleDbService;

    @BeforeEach
    void setUp() {
        timescaleDbService.initSchema();
        timescaleDbService.truncateTicks();
    }

    @Test
    @DisplayName("Verify hypertable tick ingestion and dynamic time_bucket candlestick generation")
    void testTimeBucketCandlesticks() {
        Instant baseTime = Instant.parse("2026-03-25T10:00:00Z");
        List<TimescaleTick> ticks = new ArrayList<>();

        // Minute 0: AAPL trades (150.0 -> 152.5 -> 149.5 -> 151.0)
        ticks.add(TimescaleTick.builder().time(baseTime.plus(5, ChronoUnit.SECONDS)).symbol("AAPL").price(150.0).volume(100L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(15, ChronoUnit.SECONDS)).symbol("AAPL").price(152.5).volume(200L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(30, ChronoUnit.SECONDS)).symbol("AAPL").price(149.5).volume(300L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(50, ChronoUnit.SECONDS)).symbol("AAPL").price(151.0).volume(150L).build());

        // Minute 1: AAPL trades (151.2 -> 153.0 -> 150.8 -> 152.0)
        ticks.add(TimescaleTick.builder().time(baseTime.plus(65, ChronoUnit.SECONDS)).symbol("AAPL").price(151.2).volume(120L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(80, ChronoUnit.SECONDS)).symbol("AAPL").price(153.0).volume(250L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(95, ChronoUnit.SECONDS)).symbol("AAPL").price(150.8).volume(80L).build());
        ticks.add(TimescaleTick.builder().time(baseTime.plus(110, ChronoUnit.SECONDS)).symbol("AAPL").price(152.0).volume(300L).build());

        timescaleDbService.ingestTicks(ticks);

        List<TimescaleCandle> candles = timescaleDbService.getDynamicTimeBucketCandles("AAPL", "1 minute");
        assertThat(candles).hasSize(2);

        TimescaleCandle candle0 = candles.get(0);
        assertThat(candle0.getOpen()).isEqualTo(150.0);
        assertThat(candle0.getHigh()).isEqualTo(152.5);
        assertThat(candle0.getLow()).isEqualTo(149.5);
        assertThat(candle0.getClose()).isEqualTo(151.0);
        assertThat(candle0.getVolume()).isEqualTo(750L);

        TimescaleCandle candle1 = candles.get(1);
        assertThat(candle1.getOpen()).isEqualTo(151.2);
        assertThat(candle1.getHigh()).isEqualTo(153.0);
        assertThat(candle1.getLow()).isEqualTo(150.8);
        assertThat(candle1.getClose()).isEqualTo(152.0);
        assertThat(candle1.getVolume()).isEqualTo(750L);
    }

    @Test
    @DisplayName("Verify TimescaleDB continuous aggregate materialized view refresh and querying")
    void testContinuousAggregate() {
        Instant baseTime = Instant.parse("2026-03-25T11:00:00Z");
        List<TimescaleTick> ticks = List.of(
                TimescaleTick.builder().time(baseTime.plus(10, ChronoUnit.SECONDS)).symbol("MSFT").price(420.0).volume(500L).build(),
                TimescaleTick.builder().time(baseTime.plus(20, ChronoUnit.SECONDS)).symbol("MSFT").price(425.0).volume(200L).build(),
                TimescaleTick.builder().time(baseTime.plus(40, ChronoUnit.SECONDS)).symbol("MSFT").price(418.0).volume(300L).build(),
                TimescaleTick.builder().time(baseTime.plus(55, ChronoUnit.SECONDS)).symbol("MSFT").price(422.0).volume(400L).build()
        );

        timescaleDbService.ingestTicks(ticks);
        timescaleDbService.refreshContinuousAggregate();

        List<TimescaleCandle> candles = timescaleDbService.getContinuousCandlesticks("MSFT");
        assertThat(candles).isNotEmpty();

        TimescaleCandle candle = candles.get(0);
        assertThat(candle.getSymbol()).isEqualTo("MSFT");
        assertThat(candle.getOpen()).isEqualTo(420.0);
        assertThat(candle.getHigh()).isEqualTo(425.0);
        assertThat(candle.getLow()).isEqualTo(418.0);
        assertThat(candle.getClose()).isEqualTo(422.0);
        assertThat(candle.getVolume()).isEqualTo(1400L);
    }

    @Test
    @DisplayName("Verify TimescaleDB columnar compression policies and compression stats")
    void testColumnarCompression() {
        Instant baseTime = Instant.parse("2026-03-25T12:00:00Z");
        List<TimescaleTick> ticks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ticks.add(TimescaleTick.builder()
                    .time(baseTime.plus(i * 10, ChronoUnit.SECONDS))
                    .symbol("NVDA")
                    .price(130.0 + (i % 5))
                    .volume(1000L + (i * 10))
                    .build());
        }
        timescaleDbService.ingestTicks(ticks);

        timescaleDbService.enableAndRunCompression();
        TimescaleCompressionStat stats = timescaleDbService.getCompressionStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getHypertableName()).isEqualTo("market_ticks");
        assertThat(stats.getCompressedChunks()).isGreaterThanOrEqualTo(1L);
    }
}
