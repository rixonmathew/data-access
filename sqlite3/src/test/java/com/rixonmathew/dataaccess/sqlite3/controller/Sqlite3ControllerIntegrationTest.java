package com.rixonmathew.dataaccess.sqlite3.controller;

import com.rixonmathew.dataaccess.sqlite3.config.TestConfig;
import com.rixonmathew.dataaccess.sqlite3.model.Employee;
import com.rixonmathew.dataaccess.sqlite3.service.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Sqlite3Controller.
 * Uses WebMvcTest to test the controller in isolation.
 */
@WebMvcTest(Sqlite3Controller.class)
@Import(TestConfig.class)
public class Sqlite3ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryService queryService;

    @Test
    public void testGetAllEmployees() throws Exception {
        // Mock the service response
        List<Employee> mockEmployees = Arrays.asList(
                new Employee("1", "John", "Doe", "john.doe@example.com", "Engineering"),
                new Employee("2", "Jane", "Smith", "jane.smith@example.com", "Marketing"),
                new Employee("3", "Bob", "Johnson", "bob.johnson@example.com", "Finance"),
                new Employee("4", "Alice", "Williams", "alice.williams@example.com", "HR"),
                new Employee("5", "Charlie", "Brown", "charlie.brown@example.com", "Engineering")
        );
        when(queryService.query("SELECT * FROM employees")).thenReturn(mockEmployees);

        // Call the /employees endpoint
        mockMvc.perform(get("/api/sqlite3/employees")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].firstName", is("John")))
                .andExpect(jsonPath("$[0].lastName", is("Doe")))
                .andExpect(jsonPath("$[0].email", is("john.doe@example.com")))
                .andExpect(jsonPath("$[0].department", is("Engineering")));
    }

    @Test
    public void testQueryWithParameter() throws Exception {
        // Mock the service response for engineers
        List<Employee> mockEngineers = Arrays.asList(
                new Employee("1", "John", "Doe", "john.doe@example.com", "Engineering"),
                new Employee("5", "Charlie", "Brown", "charlie.brown@example.com", "Engineering")
        );
        when(queryService.query(anyString())).thenReturn(mockEngineers);

        // Call the /query endpoint
        mockMvc.perform(get("/api/sqlite3/query")
                .param("query", "SELECT * FROM employees WHERE department = 'Engineering'")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].department", is("Engineering")))
                .andExpect(jsonPath("$[1].department", is("Engineering")));
    }
}
