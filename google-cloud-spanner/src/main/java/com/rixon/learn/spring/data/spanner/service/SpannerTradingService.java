package com.rixon.learn.spring.data.spanner.service;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.*;
import com.rixon.learn.spring.data.spanner.model.AccountHierarchySummary;
import com.rixon.learn.spring.data.spanner.model.CustomerOrder;
import com.rixon.learn.spring.data.spanner.model.TradeExecution;
import com.rixon.learn.spring.data.spanner.model.TradingAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpannerTradingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpannerTradingService.class);

    private final DatabaseClient databaseClient;

    public SpannerTradingService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public void createAccount(TradingAccount account) {
        Mutation mutation = Mutation.newInsertBuilder("TradingAccounts")
                .set("account_id").to(account.getAccountId())
                .set("account_name").to(account.getAccountName())
                .set("currency").to(account.getCurrency())
                .set("balance").to(account.getBalance())
                .set("created_at").to(Value.COMMIT_TIMESTAMP)
                .build();
        databaseClient.write(List.of(mutation));
        LOGGER.info("Created TradingAccount: {}", account.getAccountId());
    }

    public void placeOrder(CustomerOrder order) {
        Mutation mutation = Mutation.newInsertBuilder("CustomerOrders")
                .set("account_id").to(order.getAccountId())
                .set("order_id").to(order.getOrderId())
                .set("symbol").to(order.getSymbol())
                .set("side").to(order.getSide())
                .set("price").to(order.getPrice())
                .set("quantity").to(order.getQuantity())
                .set("status").to(order.getStatus())
                .set("created_at").to(Value.COMMIT_TIMESTAMP)
                .build();
        databaseClient.write(List.of(mutation));
        LOGGER.info("Placed CustomerOrder: {} for Account: {}", order.getOrderId(), order.getAccountId());
    }

    public Timestamp executeTradeAtomic(String accountId, String orderId, String executionId, BigDecimal execPrice, long execQuantity) {
        LOGGER.info("Executing atomic trade across interleaved tables for Account: {}, Order: {}", accountId, orderId);

        TransactionRunner runner = databaseClient.readWriteTransaction();
        runner.run(transaction -> {
            // 1. Read parent order and account to verify state
            Struct orderRow = transaction.readRow("CustomerOrders",
                    Key.of(accountId, orderId),
                    List.of("side", "status", "quantity"));

            if (orderRow == null) {
                throw new IllegalArgumentException("Order not found: " + orderId);
            }

            String side = orderRow.getString("side");

            Struct accountRow = transaction.readRow("TradingAccounts",
                    Key.of(accountId),
                    List.of("balance"));

            if (accountRow == null) {
                throw new IllegalArgumentException("Account not found: " + accountId);
            }

            BigDecimal currentBalance = accountRow.getBigDecimal("balance");
            BigDecimal tradeValue = execPrice.multiply(BigDecimal.valueOf(execQuantity));
            BigDecimal updatedBalance = "BUY".equalsIgnoreCase(side)
                    ? currentBalance.subtract(tradeValue)
                    : currentBalance.add(tradeValue);

            // 2. Insert into TradeExecutions (Interleaved child of CustomerOrders)
            Mutation insertExecution = Mutation.newInsertBuilder("TradeExecutions")
                    .set("account_id").to(accountId)
                    .set("order_id").to(orderId)
                    .set("execution_id").to(executionId)
                    .set("execution_price").to(execPrice)
                    .set("executed_quantity").to(execQuantity)
                    .set("executed_at").to(Value.COMMIT_TIMESTAMP)
                    .build();

            // 3. Update CustomerOrders status to FILLED
            Mutation updateOrder = Mutation.newUpdateBuilder("CustomerOrders")
                    .set("account_id").to(accountId)
                    .set("order_id").to(orderId)
                    .set("status").to("FILLED")
                    .build();

            // 4. Update TradingAccounts balance
            Mutation updateAccount = Mutation.newUpdateBuilder("TradingAccounts")
                    .set("account_id").to(accountId)
                    .set("balance").to(updatedBalance)
                    .build();

            transaction.buffer(List.of(insertExecution, updateOrder, updateAccount));
            return null;
        });
        return runner.getCommitTimestamp();
    }

    public AccountHierarchySummary getAccountHierarchySummary(String accountId) {
        String sql = "SELECT a.account_id, a.account_name, a.balance, " +
                     "       COUNT(DISTINCT o.order_id) as order_count, " +
                     "       COUNT(DISTINCT e.execution_id) as execution_count, " +
                     "       COALESCE(SUM(e.executed_quantity), 0) as total_executed_quantity, " +
                     "       COALESCE(SUM(e.execution_price * e.executed_quantity), 0) as total_executed_notional " +
                     "FROM TradingAccounts a " +
                     "LEFT JOIN CustomerOrders o ON a.account_id = o.account_id " +
                     "LEFT JOIN TradeExecutions e ON o.account_id = e.account_id AND o.order_id = e.order_id " +
                     "WHERE a.account_id = @accountId " +
                     "GROUP BY a.account_id, a.account_name, a.balance";

        Statement statement = Statement.newBuilder(sql)
                .bind("accountId").to(accountId)
                .build();

        try (ResultSet rs = databaseClient.singleUse().executeQuery(statement)) {
            if (rs.next()) {
                return AccountHierarchySummary.builder()
                        .accountId(rs.getString("account_id"))
                        .accountName(rs.getString("account_name"))
                        .balance(rs.getBigDecimal("balance"))
                        .orderCount(rs.getLong("order_count"))
                        .executionCount(rs.getLong("execution_count"))
                        .totalExecutedQuantity(rs.getLong("total_executed_quantity"))
                        .totalExecutedNotional(rs.getBigDecimal("total_executed_notional"))
                        .build();
            }
            return null;
        }
    }

    public void deleteAccountCascade(String accountId) {
        Mutation mutation = Mutation.delete("TradingAccounts", Key.of(accountId));
        databaseClient.write(List.of(mutation));
        LOGGER.info("Deleted account {} with cascade to orders and executions.", accountId);
    }
}
