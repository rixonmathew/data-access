package com.rixon.learn.spring.data.ducklake.controller;

import com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration;
import com.rixon.learn.spring.data.ducklake.service.DuckDBService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.sql.SQLException;

import static com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration.DATA_BUCKET;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class DuckDBControllerIntegrationTest {

    private static final String TEST_TABLE = "controller_test_table";
    private static final String S3_PATH = "s3://" + DATA_BUCKET + "/controller/test-data.csv";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private DuckDBService duckDBService;

    @BeforeEach
    void setUp() throws Exception {
        s3Client.putObject(PutObjectRequest.builder().bucket(DATA_BUCKET).key("controller/test-data.csv").build(),
                RequestBody.fromString("id,name,value\n1,Item 1,100\n2,Item 2,200\n3,Item 3,300"));

        mockMvc.perform(post("/api/duckdb/tables")
                        .param("tableName", TEST_TABLE)
                        .param("path", S3_PATH)
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName", is(TEST_TABLE)))
                .andExpect(jsonPath("$.path", is(S3_PATH)));
    }

    @AfterEach
    void tearDown() throws SQLException {
        duckDBService.executeStatement("DROP TABLE IF EXISTS " + TEST_TABLE);
    }

    @Test
    void testQueryEndpoint() throws Exception {
        mockMvc.perform(post("/api/duckdb/query")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("SELECT * FROM " + TEST_TABLE + " ORDER BY id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.count", is(3)))
                .andExpect(jsonPath("$.results[0].id", is(1)))
                .andExpect(jsonPath("$.results[0].name", is("Item 1")))
                .andExpect(jsonPath("$.results[0].value", is(100)));
    }

    @Test
    void testStatementEndpoint() throws Exception {
        mockMvc.perform(post("/api/duckdb/query")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("DELETE FROM " + TEST_TABLE + " WHERE id = 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Statement executed successfully")));

        mockMvc.perform(get("/api/duckdb/tables/" + TEST_TABLE))
                .andExpect(jsonPath("$.count", is(2)));
    }

    @Test
    void testGetTableDataEndpoint() throws Exception {
        mockMvc.perform(get("/api/duckdb/tables/" + TEST_TABLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName", is(TEST_TABLE)))
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.count", is(3)))
                .andExpect(jsonPath("$.results[*].name", hasItem("Item 2")));
    }

    @Test
    void testInvalidRequestsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/duckdb/tables/{name}", "x UNION SELECT 1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid identifier")));

        mockMvc.perform(post("/api/duckdb/tables")
                        .param("tableName", "missing_table")
                        .param("path", "s3://" + DATA_BUCKET + "/does-not-exist.csv")
                        .param("format", "CSV"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/duckdb/query")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("SELECT * FROM no_such_table"))
                .andExpect(status().isBadRequest());
    }
}
