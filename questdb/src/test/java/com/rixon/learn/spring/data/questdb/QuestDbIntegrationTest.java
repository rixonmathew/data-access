package com.rixon.learn.spring.data.questdb;

import com.rixon.learn.spring.data.questdb.model.CandleStick;
import com.rixon.learn.spring.data.questdb.model.MarketTick;
import com.rixon.learn.spring.data.questdb.model.TradeQuoteMatch;
import com.rixon.learn.spring.data.questdb.model.TradeRecord;
import com.rixon.learn.spring.data.questdb.service.QuestDbAnalyticsService;
import com.rixon.learn.spring.data.questdb.service.QuestDbIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.QuestDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class QuestDbIntegrationTest {

    @Container
    static QuestDBContainer questdb = new QuestDBContainer("questdb/questdb:8.3.2");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format("jdbc:postgresql://%s:%d/qdb",
                questdb.getHost(), questdb.getMappedPort(8812)));
        registry.add("spring.datasource.username", () -> "admin");
        registry.add("spring.datasource.password", () -> "quest");
        registry.add("questdb.http-port", () -> questdb.getMappedPort(9000));
        registry.add("questdb.ilp-port", () -> questdb.getMappedPort(9009));
    }

    @Autowired
    private QuestDbIngestionService ingestionService;

    @Autowired
    private QuestDbAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        ingestionService.initSchema();
        ingestionService.truncateTables();
    }

    @Test
    @DisplayName("LATEST ON: Resolves latest quote per ticker without full table scan")
    void testLatestOnSymbol() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        List<MarketTick> ticks = List.of(
                MarketTick.builder().symbol("AAPL").bid(180.10).ask(180.15).lastPrice(180.12).volume(100).timestamp(base.minusSeconds(10)).build(),
                MarketTick.builder().symbol("AAPL").bid(180.50).ask(180.55).lastPrice(180.52).volume(200).timestamp(base).build(),
                MarketTick.builder().symbol("MSFT").bid(420.00).ask(420.10).lastPrice(420.05).volume(150).timestamp(base.minusSeconds(5)).build(),
                MarketTick.builder().symbol("MSFT").bid(421.00).ask(421.10).lastPrice(421.05).volume(300).timestamp(base).build()
        );

        ingestionService.ingestTicksJdbc(ticks);

        List<MarketTick> latest = analyticsService.getLatestQuotes();

        assertThat(latest).hasSize(2);

        MarketTick aapl = latest.stream().filter(t -> t.getSymbol().equals("AAPL")).findFirst().orElseThrow();
        assertThat(aapl.getLastPrice()).isEqualTo(180.52);

        MarketTick msft = latest.stream().filter(t -> t.getSymbol().equals("MSFT")).findFirst().orElseThrow();
        assertThat(msft.getLastPrice()).isEqualTo(421.05);
    }

    @Test
    @DisplayName("SAMPLE BY: Generates 1-second OHLCV candlesticks and VWAP natively")
    void testSampleByCandlestickGeneration() {
        Instant base = Instant.parse("2026-09-25T10:00:00Z");

        List<MarketTick> ticks = List.of(
                MarketTick.builder().symbol("NVDA").bid(120.0).ask(120.1).lastPrice(120.00).volume(100).timestamp(base).build(),
                MarketTick.builder().symbol("NVDA").bid(122.0).ask(122.1).lastPrice(122.50).volume(200).timestamp(base.plusMillis(300)).build(),
                MarketTick.builder().symbol("NVDA").bid(119.5).ask(119.6).lastPrice(119.50).volume(150).timestamp(base.plusMillis(600)).build(),
                MarketTick.builder().symbol("NVDA").bid(121.0).ask(121.1).lastPrice(121.00).volume(300).timestamp(base.plusMillis(900)).build()
        );

        ingestionService.ingestTicksJdbc(ticks);

        List<CandleStick> candles = analyticsService.generateCandlesticks("NVDA", "1s");

        assertThat(candles).isNotEmpty();
        CandleStick candle = candles.get(0);
        assertThat(candle.getSymbol()).isEqualTo("NVDA");
        assertThat(candle.getOpen()).isEqualTo(120.00);
        assertThat(candle.getHigh()).isEqualTo(122.50);
        assertThat(candle.getLow()).isEqualTo(119.50);
        assertThat(candle.getClose()).isEqualTo(121.00);
        assertThat(candle.getVolume()).isEqualTo(750);
    }

    @Test
    @DisplayName("ASOF JOIN: Reconciles trades with prevailing market quote at trade execution time")
    void testAsofJoinReconciliation() {
        Instant t1 = Instant.parse("2026-09-25T11:00:01.000Z");
        Instant t2 = Instant.parse("2026-09-25T11:00:01.250Z"); // Trade between t1 and t3
        Instant t3 = Instant.parse("2026-09-25T11:00:02.000Z");
        Instant t4 = Instant.parse("2026-09-25T11:00:02.100Z"); // Trade after t3

        // Quotes at t1 and t3
        List<MarketTick> quotes = List.of(
                MarketTick.builder().symbol("TSLA").bid(240.00).ask(240.10).lastPrice(240.05).volume(50).timestamp(t1).build(),
                MarketTick.builder().symbol("TSLA").bid(241.00).ask(241.20).lastPrice(241.10).volume(80).timestamp(t3).build()
        );
        ingestionService.ingestTicksJdbc(quotes);

        // Trades at t2 and t4
        List<TradeRecord> trades = List.of(
                TradeRecord.builder().tradeId("TRD-001").symbol("TSLA").price(240.08).quantity(100).side("BUY").timestamp(t2).build(),
                TradeRecord.builder().tradeId("TRD-002").symbol("TSLA").price(241.15).quantity(200).side("SELL").timestamp(t4).build()
        );
        ingestionService.ingestTradesJdbc(trades);

        List<TradeQuoteMatch> matches = analyticsService.matchTradesWithQuotesAsof("TSLA");

        assertThat(matches).hasSize(2);

        TradeQuoteMatch m1 = matches.get(0);
        assertThat(m1.getTradeId()).isEqualTo("TRD-001");
        assertThat(m1.getBidPrice()).isEqualTo(240.00); // Matched prevailing quote at t1
        assertThat(m1.getAskPrice()).isEqualTo(240.10);
        assertThat(m1.getSpread()).isCloseTo(0.10, org.assertj.core.api.Assertions.within(0.001));

        TradeQuoteMatch m2 = matches.get(1);
        assertThat(m2.getTradeId()).isEqualTo("TRD-002");
        assertThat(m2.getBidPrice()).isEqualTo(241.00); // Matched prevailing quote at t3
        assertThat(m2.getAskPrice()).isEqualTo(241.20);
    }

    @Test
    @DisplayName("ILP: Streams market ticks over TCP socket using InfluxDB Line Protocol")
    void testIlpSocketIngestion() throws InterruptedException {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        MarketTick tick = MarketTick.builder()
                .symbol("AMZN")
                .bid(195.10)
                .ask(195.20)
                .lastPrice(195.15)
                .volume(500)
                .timestamp(now)
                .build();

        int ilpPort = questdb.getMappedPort(9009);
        ingestionService.ingestViaIlpSocket(questdb.getHost(), ilpPort, List.of(tick.toIlp()));

        // Allow micro-batch flush in QuestDB
        Thread.sleep(1500);

        List<MarketTick> latest = analyticsService.getLatestQuotes();
        assertThat(latest.stream().anyMatch(t -> t.getSymbol().equals("AMZN"))).isTrue();
    }
}
