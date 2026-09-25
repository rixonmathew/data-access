package com.rixon.learn.spring.data.risingwave;

import com.rixon.learn.spring.data.risingwave.model.MarketTrade;
import com.rixon.learn.spring.data.risingwave.model.RealtimeVwap;
import com.rixon.learn.spring.data.risingwave.model.WashTradeAlert;
import com.rixon.learn.spring.data.risingwave.service.RisingWaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
public class RisingWaveIntegrationTest {

    @Container
    static GenericContainer<?> rwContainer = new GenericContainer<>(
            DockerImageName.parse("risingwavelabs/risingwave:latest")
    )
            .withExposedPorts(4566)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                String.format("jdbc:postgresql://%s:%d/dev", rwContainer.getHost(), rwContainer.getMappedPort(4566)));
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private RisingWaveService risingWaveService;

    @BeforeEach
    void setUp() {
        risingWaveService.initSchema();
        risingWaveService.truncateTrades();
    }

    @Test
    @DisplayName("Verify real-time continuous VWAP computed incrementally in RisingWave materialized view")
    void testRealtimeContinuousVwap() {
        Instant now = Instant.parse("2026-03-25T14:00:00Z");

        List<MarketTrade> trades = List.of(
                MarketTrade.builder().tradeId("T1").traderId("TR1").symbol("TSLA").price(200.0).quantity(100L).side("BUY").tradeTime(now).build(),
                MarketTrade.builder().tradeId("T2").traderId("TR2").symbol("TSLA").price(206.0).quantity(200L).side("SELL").tradeTime(now.plus(5, ChronoUnit.SECONDS)).build()
        );

        risingWaveService.ingestTrades(trades);

        RealtimeVwap vwap = risingWaveService.getRealtimeVwap("TSLA");
        assertThat(vwap).isNotNull();
        assertThat(vwap.getSymbol()).isEqualTo("TSLA");
        assertThat(vwap.getTradeCount()).isEqualTo(2L);
        assertThat(vwap.getTotalVolume()).isEqualTo(300L);
        // (100 * 200 + 200 * 206) / 300 = 61200 / 300 = 204.0
        assertThat(vwap.getVwap()).isCloseTo(204.0, within(0.001));
    }

    @Test
    @DisplayName("Verify streaming continuous surveillance: detect wash trading within 30-second window")
    void testWashTradingSurveillanceAlert() {
        Instant now = Instant.parse("2026-03-25T14:30:00Z");

        List<MarketTrade> trades = List.of(
                // Rogue trader executes BUY then SELL on AAPL within 5 seconds -> Wash trade
                MarketTrade.builder().tradeId("W-B1").traderId("ROGUE_TRADER_1").symbol("AAPL").price(180.0).quantity(500L).side("BUY").tradeTime(now).build(),
                MarketTrade.builder().tradeId("W-S1").traderId("ROGUE_TRADER_1").symbol("AAPL").price(180.1).quantity(500L).side("SELL").tradeTime(now.plus(5, ChronoUnit.SECONDS)).build(),

                // Two different legitimate traders trading MSFT -> Not a wash trade
                MarketTrade.builder().tradeId("L-B1").traderId("LEGIT_BUYER").symbol("MSFT").price(400.0).quantity(1000L).side("BUY").tradeTime(now).build(),
                MarketTrade.builder().tradeId("L-S1").traderId("LEGIT_SELLER").symbol("MSFT").price(400.0).quantity(1000L).side("SELL").tradeTime(now.plus(10, ChronoUnit.SECONDS)).build()
        );

        risingWaveService.ingestTrades(trades);

        List<WashTradeAlert> alerts = risingWaveService.getWashTradeAlerts();
        assertThat(alerts).hasSize(1);

        WashTradeAlert alert = alerts.get(0);
        assertThat(alert.getTraderId()).isEqualTo("ROGUE_TRADER_1");
        assertThat(alert.getSymbol()).isEqualTo("AAPL");
        assertThat(alert.getBuyTradeId()).isEqualTo("W-B1");
        assertThat(alert.getSellTradeId()).isEqualTo("W-S1");
        assertThat(alert.getBuyPrice()).isEqualTo(180.0);
        assertThat(alert.getSellPrice()).isEqualTo(180.1);
        assertThat(alert.getVolume()).isEqualTo(500L);
    }
}
