package com.rixon.model.util;

import com.rixon.model.account.Account;
import com.rixon.model.account.AccountStatus;
import com.rixon.model.account.AccountType;
import com.rixon.model.contract.Contract;
import com.rixon.model.instrument.AssetClass;
import com.rixon.model.instrument.Instrument;
import com.rixon.model.market.MarketQuote;
import com.rixon.model.order.Order;
import com.rixon.model.order.OrderSide;
import com.rixon.model.order.OrderStatus;
import com.rixon.model.order.OrderType;
import com.rixon.model.risk.CounterpartyRelationship;
import com.rixon.model.risk.RelationshipType;
import com.rixon.model.trade.TradeExecution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class DataGeneratorUtils {

    private static final String[] TICKERS = {
            "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA",
            "META", "TSLA", "BRK.B", "JPM", "V"
    };

    private static final String[] COMPANY_NAMES = {
            "Apple Inc.", "Microsoft Corporation", "Alphabet Inc.", "Amazon.com Inc.", "NVIDIA Corporation",
            "Meta Platforms Inc.", "Tesla Inc.", "Berkshire Hathaway Inc.", "JPMorgan Chase & Co.", "Visa Inc."
    };

    private static final String[] FIRST_NAMES = {
            "James", "Mary", "John", "Patricia", "Robert",
            "Jennifer", "Michael", "Linda", "William", "Elizabeth",
            "David", "Barbara", "Richard", "Susan", "Joseph"
    };

    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones",
            "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
            "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson"
    };

    private static final String[] EXCHANGES = {"NASDAQ", "NYSE", "LSE", "TSE", "HKEX"};

    // --- Contract Generators (Backward Compatibility) ---

    public static List<Contract> randomContracts(long count) {
        return LongStream.range(0, count)
                .mapToObj(DataGeneratorUtils::randomContract)
                .collect(Collectors.toList());
    }

    private static Contract randomContract(long index) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setType("LOAN");
        contract.setAssetIdentifierType("CUSIP");
        contract.setAssetIdentifier("C1123323");
        contract.setQuantity(BigDecimal.valueOf(100));
        contract.setTradeDate(LocalDate.now());
        contract.setSettlementDate(LocalDate.now().plusDays(3));
        contract.setComments(String.valueOf(index));
        return contract;
    }

    // --- Instrument Generators ---

    public static List<Instrument> randomInstruments(long count) {
        return LongStream.range(0, count)
                .mapToObj(DataGeneratorUtils::randomInstrument)
                .collect(Collectors.toList());
    }

    public static Instrument randomInstrument(long id) {
        int idx = (int) (Math.abs(id) % TICKERS.length);
        String ticker = TICKERS[idx];
        String name = COMPANY_NAMES[idx];
        BigDecimal price = BigDecimal.valueOf(50 + (idx * 35.5)).setScale(2, RoundingMode.HALF_UP);

        return Instrument.builder()
                .id(id > 0 ? id : null)
                .name(name)
                .type("SHARE")
                .ticker(ticker)
                .isin("US" + String.format("%09d", Math.abs(ticker.hashCode())) + "0")
                .assetClass(AssetClass.EQUITY)
                .currency("USD")
                .lastPrice(price)
                .metadata(String.format("{\"ticker\":\"%s\",\"sector\":\"Technology\",\"inceptionDate\":\"01-Jan-1980\"}", ticker))
                .build();
    }

    // --- Account Generators ---

    public static List<Account> randomAccounts(long count) {
        return LongStream.range(0, count)
                .mapToObj(DataGeneratorUtils::randomAccount)
                .collect(Collectors.toList());
    }

    public static Account randomAccount(long index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String holderName = firstName + " " + lastName;
        String email = (firstName + "." + lastName + index + "@trading-poc.com").toLowerCase();
        String accountNumber = String.format("ACC-%06d", index + 1);

        AccountType[] types = AccountType.values();
        AccountType type = types[random.nextInt(types.length)];

        BigDecimal balance = BigDecimal.valueOf(random.nextDouble(10_000.0, 1_000_000.0))
                .setScale(2, RoundingMode.HALF_UP);

        return Account.builder()
                .accountNumber(accountNumber)
                .holderName(holderName)
                .email(email)
                .type(type)
                .balance(balance)
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .version(null)
                .createdAt(Instant.now().minus(random.nextInt(1, 365), ChronoUnit.DAYS))
                .updatedAt(Instant.now())
                .build();
    }

    // --- Order Generators ---

    public static List<Order> randomOrders(long count) {
        List<Account> accounts = randomAccounts(Math.min(count, 20));
        return randomOrdersForAccounts(accounts, count);
    }

    public static List<Order> randomOrdersForAccounts(List<Account> accounts, long count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return LongStream.range(0, count)
                .mapToObj(i -> {
                    Account account = accounts.get(random.nextInt(accounts.size()));
                    String ticker = TICKERS[random.nextInt(TICKERS.length)];
                    OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
                    OrderType type = random.nextInt(10) < 7 ? OrderType.LIMIT : OrderType.MARKET;
                    BigDecimal price = BigDecimal.valueOf(random.nextDouble(50.0, 500.0))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal quantity = BigDecimal.valueOf(random.nextInt(1, 50) * 10L);

                    return Order.builder()
                            .orderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .accountNumber(account.getAccountNumber())
                            .ticker(ticker)
                            .side(side)
                            .orderType(type)
                            .price(price)
                            .quantity(quantity)
                            .filledQuantity(BigDecimal.ZERO)
                            .status(OrderStatus.NEW)
                            .createdAt(Instant.now().minus(random.nextInt(1, 100), ChronoUnit.HOURS))
                            .updatedAt(Instant.now())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // --- Trade Execution Generators ---

    public static List<TradeExecution> randomTradeExecutions(long count) {
        List<Order> orders = randomOrders(count);
        return randomTradeExecutionsForOrders(orders);
    }

    public static List<TradeExecution> randomTradeExecutionsForOrders(List<Order> orders) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return orders.stream()
                .map(order -> {
                    BigDecimal executedPrice = order.getPrice() != null ? order.getPrice() :
                            BigDecimal.valueOf(random.nextDouble(100.0, 300.0)).setScale(2, RoundingMode.HALF_UP);
                    String exchange = EXCHANGES[random.nextInt(EXCHANGES.length)];
                    String counterparty = String.format("ACC-%06d", random.nextInt(100, 999));

                    return TradeExecution.builder()
                            .tradeId("TRD-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase())
                            .orderId(order.getOrderId())
                            .accountNumber(order.getAccountNumber())
                            .ticker(order.getTicker())
                            .side(order.getSide())
                            .executedPrice(executedPrice)
                            .executedQuantity(order.getQuantity())
                            .executionTime(Instant.now().minus(random.nextInt(1, 60), ChronoUnit.MINUTES))
                            .exchange(exchange)
                            .counterpartyAccountNumber(counterparty)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // --- Market Quote Generators ---

    public static List<MarketQuote> randomMarketQuotes(String ticker, int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double basePrice = 100.0 + (Math.abs(ticker.hashCode() % 100));
        List<MarketQuote> quotes = new ArrayList<>(count);
        Instant startTime = Instant.now().minus(count, ChronoUnit.MINUTES);

        for (int i = 0; i < count; i++) {
            double delta = (random.nextDouble() - 0.49) * 2.0;
            basePrice = Math.max(10.0, basePrice + delta);
            BigDecimal lastPrice = BigDecimal.valueOf(basePrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bidPrice = lastPrice.subtract(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal askPrice = lastPrice.add(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
            long volume = random.nextLong(100, 5000);

            quotes.add(new MarketQuote(
                    ticker,
                    startTime.plus(i, ChronoUnit.MINUTES),
                    bidPrice,
                    askPrice,
                    lastPrice,
                    volume
            ));
        }
        return quotes;
    }

    public static List<MarketQuote> randomMarketQuotes(int countPerTicker) {
        List<MarketQuote> allQuotes = new ArrayList<>();
        for (String ticker : TICKERS) {
            allQuotes.addAll(randomMarketQuotes(ticker, countPerTicker));
        }
        return allQuotes;
    }

    // --- Counterparty Relationship Generators (For Graph / Risk Analysis) ---

    public static List<CounterpartyRelationship> randomCounterpartyRelationships(List<String> accountNumbers, int count) {
        if (accountNumbers.size() < 2) {
            return Collections.emptyList();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        RelationshipType[] types = RelationshipType.values();
        List<CounterpartyRelationship> relationships = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int srcIdx = random.nextInt(accountNumbers.size());
            int tgtIdx = random.nextInt(accountNumbers.size());
            while (tgtIdx == srcIdx) {
                tgtIdx = random.nextInt(accountNumbers.size());
            }

            BigDecimal limit = BigDecimal.valueOf(random.nextDouble(500_000.0, 5_000_000.0))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal exposure = BigDecimal.valueOf(random.nextDouble(0.0, limit.doubleValue()))
                    .setScale(2, RoundingMode.HALF_UP);

            relationships.add(CounterpartyRelationship.builder()
                    .sourceAccountNumber(accountNumbers.get(srcIdx))
                    .targetAccountNumber(accountNumbers.get(tgtIdx))
                    .relationshipType(types[random.nextInt(types.length)])
                    .exposureLimit(limit)
                    .currentExposure(exposure)
                    .effectiveDate(Instant.now().minus(random.nextInt(1, 180), ChronoUnit.DAYS))
                    .build());
        }
        return relationships;
    }
}
