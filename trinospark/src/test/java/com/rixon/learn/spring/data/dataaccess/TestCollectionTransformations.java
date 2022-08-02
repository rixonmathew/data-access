package com.rixon.learn.spring.data.dataaccess;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.fail;

public class TestCollectionTransformations {

    String[] departments = {"IT","SALES","HR","MARKETING","LEGAL","R&D"};

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
}
