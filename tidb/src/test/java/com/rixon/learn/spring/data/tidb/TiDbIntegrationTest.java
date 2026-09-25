package com.rixon.learn.spring.data.tidb;

import com.rixon.learn.spring.data.tidb.model.PortfolioRiskExposure;
import com.rixon.learn.spring.data.tidb.model.TiDbOrder;
import com.rixon.learn.spring.data.tidb.service.TiDbService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class TiDbIntegrationTest {

    @Container
    static GenericContainer<?> tidbContainer = new GenericContainer<>(
            DockerImageName.parse("pingcap/tidb:v8.5.0")
    )
            .withExposedPorts(4000)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                String.format("jdbc:mysql://%s:%d/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        tidbContainer.getHost(), tidbContainer.getMappedPort(4000)));
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private TiDbService tiDbService;

    @BeforeEach
    void setUp() {
        tiDbService.initSchema();
        tiDbService.truncateOrders();
    }

    @Test
    @DisplayName("Verify OLTP transactional order lifecycle (insert, query, atomic state transition)")
    void testTransactionalOrderLifecycle() {
        TiDbOrder order = TiDbOrder.builder()
                .orderId("ORD-9001")
                .accountId("ACC-DESK-ALPHA")
                .symbol("NVDA")
                .side("BUY")
                .orderType("LIMIT")
                .price(new BigDecimal("125.5000"))
                .quantity(500L)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        tiDbService.createOrder(order);

        TiDbOrder fetched = tiDbService.getOrder("ORD-9001");
        assertThat(fetched).isNotNull();
        assertThat(fetched.getSymbol()).isEqualTo("NVDA");
        assertThat(fetched.getStatus()).isEqualTo("PENDING");
        assertThat(fetched.getQuantity()).isEqualTo(500L);
        assertThat(fetched.getPrice()).isEqualByComparingTo(new BigDecimal("125.5000"));

        tiDbService.updateOrderStatus("ORD-9001", "FILLED");
        TiDbOrder updated = tiDbService.getOrder("ORD-9001");
        assertThat(updated.getStatus()).isEqualTo("FILLED");
    }

    @Test
    @DisplayName("Verify real-time HTAP portfolio risk and net exposure analytics")
    void testRealTimePortfolioRiskAnalytics() {
        Instant now = Instant.now();
        List<TiDbOrder> orders = List.of(
                // AAPL trades: Buy 1000 @ $150, Sell 400 @ $155 -> Net 600, Gross 1400
                TiDbOrder.builder().orderId("O-1").accountId("ACC-RISK-01").symbol("AAPL").side("BUY").orderType("LIMIT").price(new BigDecimal("150.0000")).quantity(1000L).status("FILLED").createdAt(now).build(),
                TiDbOrder.builder().orderId("O-2").accountId("ACC-RISK-01").symbol("AAPL").side("SELL").orderType("LIMIT").price(new BigDecimal("155.0000")).quantity(400L).status("FILLED").createdAt(now).build(),

                // MSFT trades: Buy 500 @ $400 -> Net 500, Gross 500
                TiDbOrder.builder().orderId("O-3").accountId("ACC-RISK-01").symbol("MSFT").side("BUY").orderType("LIMIT").price(new BigDecimal("400.0000")).quantity(500L).status("FILLED").createdAt(now).build(),

                // Unfilled order should be excluded from filled position risk
                TiDbOrder.builder().orderId("O-4").accountId("ACC-RISK-01").symbol("AAPL").side("BUY").orderType("LIMIT").price(new BigDecimal("148.0000")).quantity(2000L).status("CANCELLED").createdAt(now).build()
        );

        tiDbService.batchCreateOrders(orders);

        List<PortfolioRiskExposure> exposures = tiDbService.getPortfolioRiskExposure("ACC-RISK-01");
        assertThat(exposures).hasSize(2);

        PortfolioRiskExposure aaplExp = exposures.get(0);
        assertThat(aaplExp.getSymbol()).isEqualTo("AAPL");
        assertThat(aaplExp.getNetPosition()).isEqualTo(600L);
        assertThat(aaplExp.getGrossVolume()).isEqualTo(1400L);
        // Gross notional: 1000 * 150 + 400 * 155 = 150000 + 62000 = 212000
        assertThat(aaplExp.getGrossNotional()).isEqualByComparingTo(new BigDecimal("212000.0000"));
        assertThat(aaplExp.getVwapBuy()).isEqualByComparingTo(new BigDecimal("150.0000"));
        assertThat(aaplExp.getVwapSell()).isEqualByComparingTo(new BigDecimal("155.0000"));

        PortfolioRiskExposure msftExp = exposures.get(1);
        assertThat(msftExp.getSymbol()).isEqualTo("MSFT");
        assertThat(msftExp.getNetPosition()).isEqualTo(500L);
        assertThat(msftExp.getGrossVolume()).isEqualTo(500L);
        assertThat(msftExp.getGrossNotional()).isEqualByComparingTo(new BigDecimal("200000.0000"));
    }
}
