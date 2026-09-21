package com.rixon.learn.spring.data.dynamodb.service;

import com.rixon.learn.spring.data.dynamodb.config.TestConfig;
import com.rixon.learn.spring.data.dynamodb.model.OrderAggregateDto;
import com.rixon.learn.spring.data.dynamodb.model.SingleTableExecutionEntity;
import com.rixon.learn.spring.data.dynamodb.model.SingleTableOrderEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
@EnabledIfDockerAvailable
class SingleTableTradingServiceIntegrationTest {

    @Autowired
    private SingleTableTradingService tradingService;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    private String tableName;

    @BeforeEach
    void setUp() {
        tableName = "trading_single_table_" + UUID.randomUUID().toString().replace("-", "");
        tradingService.createSingleTable(tableName);
        waitForTableToBeActive(tableName);
    }

    @AfterEach
    void tearDown() {
        try {
            dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        } catch (ResourceNotFoundException ignored) {
        }
    }

    private void waitForTableToBeActive(String tableName) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder().tableName(tableName).build();
            while (true) {
                var response = dynamoDbClient.describeTable(request);
                if (response.table().tableStatus() == TableStatus.ACTIVE) {
                    break;
                }
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Test 1: Single-Table aggregate retrieval - order metadata & executions in one single roundtrip query")
    void testSingleTableAggregateAndQuery() {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        String accountId = "ACC-CORP-456";

        SingleTableOrderEntity order = new SingleTableOrderEntity();
        order.setOrderId(orderId);
        order.setAccountId(accountId);
        order.setTicker("NVDA");
        order.setSide("BUY");
        order.setQuantity(1000L);
        order.setLimitPrice(125.50);
        order.setStatus("NEW");
        order.setCreatedAt(Instant.now().toString());

        tradingService.saveOrder(tableName, order);

        // Record Execution 1: partial fill of 400 shares
        SingleTableExecutionEntity exec1 = new SingleTableExecutionEntity();
        exec1.setExecutionId("EXEC-01");
        exec1.setOrderId(orderId);
        exec1.setTicker("NVDA");
        exec1.setExecutedPrice(125.40);
        exec1.setExecutedQuantity(400L);
        exec1.setExecutedAt(Instant.now().toString());
        exec1.setLiquidity("MAKER");
        tradingService.recordExecutionAtomic(tableName, exec1);

        // Verify partial fill status
        SingleTableOrderEntity intermediateOrder = tradingService.getOrder(tableName, orderId);
        assertThat(intermediateOrder.getStatus()).isEqualTo("PARTIALLY_FILLED");
        assertThat(intermediateOrder.getFilledQuantity()).isEqualTo(400L);

        // Record Execution 2: remaining 600 shares fill
        SingleTableExecutionEntity exec2 = new SingleTableExecutionEntity();
        exec2.setExecutionId("EXEC-02");
        exec2.setOrderId(orderId);
        exec2.setTicker("NVDA");
        exec2.setExecutedPrice(125.50);
        exec2.setExecutedQuantity(600L);
        exec2.setExecutedAt(Instant.now().toString());
        exec2.setLiquidity("TAKER");
        tradingService.recordExecutionAtomic(tableName, exec2);

        // Retrieve entire aggregate in a single partition query
        OrderAggregateDto aggregate = tradingService.getOrderAggregate(tableName, orderId);

        assertThat(aggregate.getOrder()).isNotNull();
        assertThat(aggregate.getOrder().getStatus()).isEqualTo("FILLED");
        assertThat(aggregate.getOrder().getFilledQuantity()).isEqualTo(1000L);
        assertThat(aggregate.getExecutions()).hasSize(2);
        assertThat(aggregate.getExecutions().stream().map(SingleTableExecutionEntity::getExecutionId).toList())
                .containsExactlyInAnyOrder("EXEC-01", "EXEC-02");
    }

    @Test
    @DisplayName("Test 2: Optimistic locking with @DynamoDbVersionAttribute rejects concurrent stale writes")
    void testOptimisticLockingWithVersionAttribute() {
        String orderId = "ORD-OPT-" + UUID.randomUUID().toString().substring(0, 8);
        SingleTableOrderEntity order = new SingleTableOrderEntity();
        order.setOrderId(orderId);
        order.setAccountId("ACC-HEDGE-1");
        order.setTicker("MSFT");
        order.setSide("SELL");
        order.setQuantity(500L);
        order.setLimitPrice(430.00);
        order.setStatus("NEW");
        order.setCreatedAt(Instant.now().toString());

        tradingService.saveOrder(tableName, order);

        // Reader A and Reader B both fetch the order with version = 1
        SingleTableOrderEntity copyA = tradingService.getOrder(tableName, orderId);
        SingleTableOrderEntity copyB = tradingService.getOrder(tableName, orderId);

        assertThat(copyA.getVersion()).isEqualTo(1L);
        assertThat(copyB.getVersion()).isEqualTo(1L);

        // Writer A updates the limit price to 435.00 -> succeeds and bumps version to 2
        copyA.setLimitPrice(435.00);
        SingleTableOrderEntity updatedA = tradingService.updateOrderWithOptimisticLocking(tableName, copyA);
        assertThat(updatedA.getVersion()).isEqualTo(2L);

        // Writer B attempts to update with stale version = 1 -> throws ConditionalCheckFailedException
        copyB.setLimitPrice(440.00);
        assertThrows(ConditionalCheckFailedException.class, () ->
                tradingService.updateOrderWithOptimisticLocking(tableName, copyB));
    }

    @Test
    @DisplayName("Test 3: Secondary index (GSI1) queries cross-cut partitions by Account and Ticker")
    void testSecondaryIndexQuery() {
        String targetAccount = "ACC-PENSION-99";
        String otherAccount = "ACC-RETAIL-01";

        // Save 2 orders for targetAccount and 1 for otherAccount
        SingleTableOrderEntity o1 = new SingleTableOrderEntity();
        o1.setOrderId("ORD-ACC-1");
        o1.setAccountId(targetAccount);
        o1.setTicker("AAPL");
        o1.setSide("BUY");
        o1.setQuantity(100L);
        o1.setLimitPrice(220.0);
        o1.setStatus("NEW");
        o1.setCreatedAt("2026-09-21T10:00:00Z");
        tradingService.saveOrder(tableName, o1);

        SingleTableOrderEntity o2 = new SingleTableOrderEntity();
        o2.setOrderId("ORD-ACC-2");
        o2.setAccountId(targetAccount);
        o2.setTicker("GOOGL");
        o2.setSide("BUY");
        o2.setQuantity(200L);
        o2.setLimitPrice(180.0);
        o2.setStatus("NEW");
        o2.setCreatedAt("2026-09-21T10:05:00Z");
        tradingService.saveOrder(tableName, o2);

        SingleTableOrderEntity o3 = new SingleTableOrderEntity();
        o3.setOrderId("ORD-ACC-3");
        o3.setAccountId(otherAccount);
        o3.setTicker("TSLA");
        o3.setSide("SELL");
        o3.setQuantity(50L);
        o3.setLimitPrice(250.0);
        o3.setStatus("NEW");
        o3.setCreatedAt("2026-09-21T10:10:00Z");
        tradingService.saveOrder(tableName, o3);

        // Query GSI1 for targetAccount orders
        List<SingleTableOrderEntity> accountOrders = tradingService.findOrdersByAccount(tableName, targetAccount);
        assertThat(accountOrders).hasSize(2);
        List<String> orderIds = accountOrders.stream().map(SingleTableOrderEntity::getOrderId).toList();
        assertThat(orderIds).containsExactlyInAnyOrder("ORD-ACC-1", "ORD-ACC-2");

        // Record execution on AAPL and query GSI1 by Ticker
        SingleTableExecutionEntity exec = new SingleTableExecutionEntity();
        exec.setExecutionId("EXEC-AAPL-01");
        exec.setOrderId("ORD-ACC-1");
        exec.setTicker("AAPL");
        exec.setExecutedPrice(219.50);
        exec.setExecutedQuantity(100L);
        exec.setExecutedAt(Instant.now().toString());
        exec.setLiquidity("TAKER");
        tradingService.recordExecutionAtomic(tableName, exec);

        List<SingleTableExecutionEntity> tickerExecutions = tradingService.findExecutionsByTicker(tableName, "AAPL");
        assertThat(tickerExecutions).hasSize(1);
        assertThat(tickerExecutions.get(0).getExecutionId()).isEqualTo("EXEC-AAPL-01");
        assertThat(tickerExecutions.get(0).getExecutedPrice()).isEqualTo(219.50);
    }
}
