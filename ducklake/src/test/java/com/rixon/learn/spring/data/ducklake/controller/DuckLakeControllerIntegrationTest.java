package com.rixon.learn.spring.data.ducklake.controller;

import com.jayway.jsonpath.JsonPath;
import com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class DuckLakeControllerIntegrationTest {

    private static final String TABLE = "api_trades";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAppendReadTimeTravelAndInspect() throws Exception {
        String first = mockMvc.perform(post("/api/ducklake/tables/{table}/trades", TABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"tradeId":"A-1","ticker":"AAPL","price":220.50,"quantity":100,"tradeDate":"2026-01-05"},
                                 {"tradeId":"A-2","ticker":"NVDA","price":125.00,"quantity":50,"tradeDate":"2026-01-05"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appended", is(2)))
                .andReturn().getResponse().getContentAsString();
        long firstSnapshot = ((Number) JsonPath.read(first, "$.snapshotId")).longValue();

        mockMvc.perform(post("/api/ducklake/tables/{table}/trades", TABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"tradeId":"A-3","ticker":"AAPL","price":221.00,"quantity":10,"tradeDate":"2026-01-06"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId", greaterThan((int) firstSnapshot)));

        mockMvc.perform(get("/api/ducklake/tables/{table}/trades", TABLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tradeId", contains("A-1", "A-2", "A-3")))
                .andExpect(jsonPath("$[0].tradeDate", is("2026-01-05")));

        mockMvc.perform(get("/api/ducklake/tables/{table}/trades", TABLE).param("version", String.valueOf(firstSnapshot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tradeId", contains("A-1", "A-2")));

        // Two AAPL commits and one NVDA commit -> three files, each under its ticker partition
        mockMvc.perform(get("/api/ducklake/tables/{table}/files", TABLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].dataFile", everyItem(containsString("/" + TABLE + "/ticker="))));

        mockMvc.perform(get("/api/ducklake/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].snapshotId", hasItem((int) firstSnapshot)));
    }

    @Test
    void testInvalidRequestsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/ducklake/tables/{table}/trades", "bad-name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid identifier")));

        mockMvc.perform(post("/api/ducklake/tables/{table}/trades", TABLE + "_invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"tradeId":"B-1","price":1.00,"quantity":1,"tradeDate":"2026-01-05"}]"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("NOT NULL")));
    }
}
