package com.rixon.learn.spring.data.dataaccess;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;
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


    String[] departments = {"IT","SALES","HR","MARKETING","LEGAL","R&D"};
    String[] accounts = {"ACC1","ACC2","ACC3"};
    String[] tickers = {"APPL","GOOG","IBM","FBOOK"};

    @Test
    public void testSecondHighestEmployeePerDepartment() {
        List<Employee> employeeList = mockEmployeeList();
        Map<String,Employee> employeesWithSecondHighestSalary = transformList(employeeList);
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
        return IntStream.rangeClosed(1,100000000)
                .mapToObj(i->{
                    Employee employee = new Employee();
                    employee.setDept(departments[random.nextInt(departments.length)]);
                    employee.setSalary(BigDecimal.TEN.multiply(BigDecimal.valueOf(random.nextInt(100))));
                    return employee;
                }).collect(Collectors.toList());

    }

    private Map<String, Employee> transformList(List<Employee> employeeList) {
         Map<String,List<Employee>> grouped  = employeeList.stream()
                .collect(Collectors.groupingBy(Employee::getDept));
        grouped.forEach((s, employees) -> employees.sort((o1, o2) -> o2.getSalary().compareTo(o1.getSalary())));
        Map<String,Employee> secondHighestSalary = new HashMap<>();
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
        collect.parallelStream().peek(localDateTradePair -> LOGGER.info("Date {} Value {}",localDateTradePair.getFirst(),localDateTradePair.getSecond())).findFirst();
    }

    private List<Trade> mockTradeList() {
        Random random = new Random();
        return IntStream.rangeClosed(1,1000000)
                .mapToObj(i->{
                    Trade trade = new Trade();
                    trade.setId(UUID.randomUUID().toString());
                    trade.setTradeDate(LocalDate.now().minusDays(random.nextLong(30)));
                    trade.setAccount(accounts[random.nextInt(accounts.length)]);
                    trade.setTicker(tickers[random.nextInt(tickers.length)]);
                    trade.setValue(BigDecimal.TEN.multiply(new BigDecimal(random.nextInt(100000))));
                    return trade;
                }).collect(Collectors.toList());
    }
}
