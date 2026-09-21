package com.rixon.model;

import com.rixon.model.account.Account;
import com.rixon.model.account.AccountStatus;
import com.rixon.model.contract.Contract;
import com.rixon.model.instrument.Instrument;
import com.rixon.model.market.MarketQuote;
import com.rixon.model.order.Order;
import com.rixon.model.order.OrderStatus;
import com.rixon.model.risk.CounterpartyRelationship;
import com.rixon.model.trade.TradeExecution;
import com.rixon.model.util.DataGeneratorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataGeneratorUtilsTest {

    @Test
    @DisplayName("Verify legacy randomContracts generator")
    void testRandomContracts() {
        List<Contract> contracts = DataGeneratorUtils.randomContracts(10);
        assertNotNull(contracts);
        assertEquals(10, contracts.size());
        for (Contract contract : contracts) {
            assertNotNull(contract.getId());
            assertEquals("LOAN", contract.getType());
            assertNotNull(contract.getTradeDate());
        }
    }

    @Test
    @DisplayName("Verify randomInstruments generator")
    void testRandomInstruments() {
        List<Instrument> instruments = DataGeneratorUtils.randomInstruments(10);
        assertNotNull(instruments);
        assertEquals(10, instruments.size());
        for (Instrument instrument : instruments) {
            assertNotNull(instrument.getName());
            assertNotNull(instrument.getTicker());
            assertNotNull(instrument.getIsin());
            assertNotNull(instrument.getLastPrice());
            assertTrue(instrument.getLastPrice().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    @DisplayName("Verify randomAccounts generator")
    void testRandomAccounts() {
        List<Account> accounts = DataGeneratorUtils.randomAccounts(25);
        assertNotNull(accounts);
        assertEquals(25, accounts.size());
        for (Account account : accounts) {
            assertNotNull(account.getAccountNumber());
            assertTrue(account.getAccountNumber().startsWith("ACC-"));
            assertNotNull(account.getHolderName());
            assertNotNull(account.getEmail());
            assertNotNull(account.getType());
            assertEquals(AccountStatus.ACTIVE, account.getStatus());
            assertNotNull(account.getBalance());
            assertTrue(account.getBalance().compareTo(BigDecimal.ZERO) > 0);
            assertEquals("USD", account.getCurrency());
            assertNull(account.getVersion());
        }
    }

    @Test
    @DisplayName("Verify randomOrders generator")
    void testRandomOrders() {
        List<Order> orders = DataGeneratorUtils.randomOrders(50);
        assertNotNull(orders);
        assertEquals(50, orders.size());
        for (Order order : orders) {
            assertNotNull(order.getOrderId());
            assertTrue(order.getOrderId().startsWith("ORD-"));
            assertNotNull(order.getAccountNumber());
            assertNotNull(order.getTicker());
            assertNotNull(order.getSide());
            assertNotNull(order.getOrderType());
            assertNotNull(order.getQuantity());
            assertTrue(order.getQuantity().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(OrderStatus.NEW, order.getStatus());
        }
    }

    @Test
    @DisplayName("Verify randomTradeExecutions generator")
    void testRandomTradeExecutions() {
        List<TradeExecution> trades = DataGeneratorUtils.randomTradeExecutions(30);
        assertNotNull(trades);
        assertEquals(30, trades.size());
        for (TradeExecution trade : trades) {
            assertNotNull(trade.getTradeId());
            assertTrue(trade.getTradeId().startsWith("TRD-"));
            assertNotNull(trade.getOrderId());
            assertNotNull(trade.getAccountNumber());
            assertNotNull(trade.getTicker());
            assertNotNull(trade.getExecutedPrice());
            assertTrue(trade.getExecutedPrice().compareTo(BigDecimal.ZERO) > 0);
            assertNotNull(trade.getExecutedQuantity());
            assertNotNull(trade.getExecutionTime());
        }
    }

    @Test
    @DisplayName("Verify randomMarketQuotes generator")
    void testRandomMarketQuotes() {
        List<MarketQuote> quotes = DataGeneratorUtils.randomMarketQuotes("AAPL", 20);
        assertNotNull(quotes);
        assertEquals(20, quotes.size());
        for (MarketQuote quote : quotes) {
            assertEquals("AAPL", quote.ticker());
            assertNotNull(quote.timestamp());
            assertNotNull(quote.lastPrice());
            assertNotNull(quote.bidPrice());
            assertNotNull(quote.askPrice());
            assertTrue(quote.askPrice().compareTo(quote.bidPrice()) >= 0);
            assertTrue(quote.volume() > 0);
        }
    }

    @Test
    @DisplayName("Verify randomCounterpartyRelationships generator")
    void testRandomCounterpartyRelationships() {
        List<String> accountNumbers = List.of("ACC-000001", "ACC-000002", "ACC-000003", "ACC-000004");
        List<CounterpartyRelationship> relationships = DataGeneratorUtils.randomCounterpartyRelationships(accountNumbers, 15);
        assertNotNull(relationships);
        assertEquals(15, relationships.size());
        for (CounterpartyRelationship rel : relationships) {
            assertNotNull(rel.getSourceAccountNumber());
            assertNotNull(rel.getTargetAccountNumber());
            assertNotEquals(rel.getSourceAccountNumber(), rel.getTargetAccountNumber());
            assertNotNull(rel.getRelationshipType());
            assertNotNull(rel.getExposureLimit());
            assertNotNull(rel.getCurrentExposure());
            assertTrue(rel.getCurrentExposure().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(rel.getCurrentExposure().compareTo(rel.getExposureLimit()) <= 0);
        }
    }
}
