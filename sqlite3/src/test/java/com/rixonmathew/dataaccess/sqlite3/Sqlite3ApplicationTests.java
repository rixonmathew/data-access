package com.rixonmathew.dataaccess.sqlite3;

import com.rixonmathew.dataaccess.sqlite3.config.TestConfig;
import com.rixonmathew.dataaccess.sqlite3.controller.Sqlite3Controller;
import com.rixonmathew.dataaccess.sqlite3.model.Employee;
import com.rixonmathew.dataaccess.sqlite3.service.QueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simple test to verify that the application components work correctly.
 * Uses WebMvcTest to test the controller in isolation.
 */
@WebMvcTest(Sqlite3Controller.class)
@Import(TestConfig.class)
@ActiveProfiles("test")
class Sqlite3ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryService queryService;

    @Test
    void contextLoads() throws Exception {
        // Mock the service response
        List<Employee> mockEmployees = Arrays.asList(
                new Employee("1", "John", "Doe", "john.doe@example.com", "Engineering")
        );
        when(queryService.query(anyString())).thenReturn(mockEmployees);

        // Call the /employees endpoint
        mockMvc.perform(get("/api/sqlite3/employees")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
