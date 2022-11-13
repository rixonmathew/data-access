package com.rixon.learn.spring.data.dataaccess;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.fail;

public class TestCollectionTransformations {

    private final static Logger LOGGER = LoggerFactory.getLogger(TestCollectionTransformations.class);


    String[] departments = {"IT", "SALES", "HR", "MARKETING", "LEGAL", "R&D"};
    String[] accounts = {"ACC1", "ACC2", "ACC3"};
    String[] tickers = {"APPL", "GOOG", "IBM", "FBOOK"};
    String[] states = {"NY", "NJ", "MA","MN","OK"};

    @Test
    public void testSecondHighestEmployeePerDepartment() {
        List<Employee> employeeList = mockEmployeeList();
        Map<String, Employee> employeesWithSecondHighestSalary = transformList(employeeList);
        employeesWithSecondHighestSalary.forEach((key, value) -> {
            int numberOfEmployeesWithHigherSalary = 0;
            for (Employee em : employeeList) {
                if (em.getDept().equals(value.getDept()) && em.getSalary().compareTo(value.getSalary()) > 0) {
                    ++numberOfEmployeesWithHigherSalary;
                }
            }
            if (numberOfEmployeesWithHigherSalary >= 2) {
                fail("Not expecting more than 1 employee in dept " + key + " but found " + numberOfEmployeesWithHigherSalary);
            }
        });
    }

    private List<Employee> mockEmployeeList() {
        Random random = new Random();
        return IntStream.rangeClosed(1, 100)
                .mapToObj(i -> {
                    Employee employee = new Employee();
                    employee.setDept(departments[random.nextInt(departments.length)]);
                    employee.setSalary(BigDecimal.TEN.multiply(BigDecimal.valueOf(random.nextInt(100))));
                    return employee;
                }).collect(Collectors.toList());

    }

    private Map<String, Employee> transformList(List<Employee> employeeList) {
        Map<String, List<Employee>> grouped = employeeList.stream()
                .collect(Collectors.groupingBy(Employee::getDept));
        grouped.forEach((s, employees) -> employees.sort((o1, o2) -> o2.getSalary().compareTo(o1.getSalary())));
        Map<String, Employee> secondHighestSalary = new HashMap<>();
        grouped.forEach((key, value) -> secondHighestSalary.put(key, value.get(1)));
        return secondHighestSalary;
    }

    @Test
    public void testTradeTransformations() {
//        LOGGER.info("{}", VM.current().details());
        List<Trade> allTrades = mockTradeList();
//        LOGGER.info("Shallow List size [{}] bytes",VM.current().sizeOf(allTrades));
//        LOGGER.info("Deep size [{}]", GraphLayout.parseInstance(allTrades).totalSize());
        List<Pair<LocalDate, Trade>> collect = allTrades
                .stream()
//                .parallelStream()
                .collect(Collectors.groupingBy(Trade::getTradeDate))
                .entrySet()
//                .stream()
                .parallelStream()
//                .peek(e -> LOGGER.info("[{}]", e.getKey()))
                .map(e -> {
//                    LOGGER.info("Finding max for date [{}] from list of size [{}]",e.getKey(),e.getValue().size());
                    List<Trade> trades = e.getValue();
                    Trade max = Collections.max(trades, Comparator.comparing(Trade::getValue));
//                    e.getValue().sort(Comparator.comparing(Trade::getValue));
                    return Pair.of(e.getKey(), max);
                }).toList();
        Optional<Pair<LocalDate, Trade>> first = collect.parallelStream().peek(localDateTradePair -> LOGGER.info("Date {} Value {}", localDateTradePair.getFirst(), localDateTradePair.getSecond())).findFirst();
        first.ifPresent((s)->LOGGER.info("{}",s));

    }

    private List<Trade> mockTradeList() {
        Random random = new Random();
        return IntStream.rangeClosed(1, 100)
                .mapToObj(i -> {
                    Trade trade = new Trade();
                    trade.setId(UUID.randomUUID().toString());
                    trade.setTradeDate(LocalDate.now().minusDays(random.nextLong(30)));
                    trade.setAccount(accounts[random.nextInt(accounts.length)]);
                    trade.setTicker(tickers[random.nextInt(tickers.length)]);
                    trade.setValue(BigDecimal.TEN.multiply(new BigDecimal(random.nextInt(100000))));
                    return trade;
                }).collect(Collectors.toList());
    }

    @Test
    public void testOrderListTransformation() {
        List<Order> orders = mockOrderList();
        Map<String, List<Order>> ordersByState = orders.parallelStream()
                .collect(Collectors.groupingBy(Order::getState));

        Map<String, List<Order>> orderByStateAndDate = orders.parallelStream().collect(Collectors.groupingBy(order -> String.format("%s:%s", order.getState(), order.getOrderDate())));

        ordersByState.forEach((s, orders1) -> LOGGER.info("State [{}] and order values [{}]",s,orders1.stream().map(Order::getValue).sorted(Comparator.reverseOrder()).limit(10).collect(Collectors.toList())));

        orderByStateAndDate.forEach((s, orders1) -> LOGGER.info("State and Date [{}] and order values [{}]",s,orders1.stream().map(Order::getValue).sorted(Comparator.reverseOrder()).limit(10).collect(Collectors.toList())));

        Map<String, List<Order>> highestOrdersPerState = ordersByState.entrySet()
                .parallelStream()
                .map(entry -> Map.of(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparing(Order::getValue).reversed()).limit(3).toList()))
                .flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        highestOrdersPerState.forEach((s, orders1) -> LOGGER.info("State [{}] and order Values [{}]",s,orders1.stream().map(Order::getValue).collect(Collectors.toList())));

    }

    private List<Order> mockOrderList() {
        Random random = new Random();
        return IntStream.rangeClosed(1, 100)
                .mapToObj(i -> {
                    Order order = new Order();
                    order.setOrderId(UUID.randomUUID().toString());
                    order.setCustomerId(UUID.randomUUID().toString());
                    order.setOrderDate(LocalDate.now().minusDays(random.nextLong(30)));
                    order.setState(states[random.nextInt(states.length)]);
                    order.setValue(BigDecimal.TEN.multiply(new BigDecimal(random.nextInt(100000))));
                    return order;
                }).collect(Collectors.toList());
    }

}
