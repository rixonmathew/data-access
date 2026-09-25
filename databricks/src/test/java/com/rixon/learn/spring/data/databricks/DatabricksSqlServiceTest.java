package com.rixon.learn.spring.data.databricks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.guard.CostGuardViolationException;
import com.rixon.learn.spring.data.databricks.guard.DatabricksCostGuard;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionRequest;
import com.rixon.learn.spring.data.databricks.model.SqlExecutionResult;
import com.rixon.learn.spring.data.databricks.service.DatabricksRestClient;
import com.rixon.learn.spring.data.databricks.service.DatabricksSqlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabricksSqlServiceTest {

    @Mock
    private DatabricksRestClient restClient;

    private DatabricksProperties properties;
    private DatabricksCostGuard costGuard;
    private DatabricksSqlService sqlService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new DatabricksProperties();
        properties.setFreeTierOnly(true);
        properties.setMaxStatementTimeoutSeconds(30);
        properties.setMaxQueryRows(100);
        costGuard = new DatabricksCostGuard(properties);
        sqlService = new DatabricksSqlService(restClient, costGuard, properties);
    }

    @Test
    @DisplayName("executeGuardedStatement executes valid query and parses results")
    void testExecuteGuardedStatement() throws Exception {
        String json = """
                {
                  "statement_id": "stmt-777",
                  "status": { "state": "SUCCEEDED" },
                  "manifest": {
                    "schema": {
                      "columns": [
                        { "name": "c_custkey", "type_text": "BIGINT" },
                        { "name": "c_name", "type_text": "STRING" }
                      ]
                    },
                    "total_row_count": 2
                  },
                  "result": {
                    "data_array": [
                      [ "1", "Customer#000000001" ],
                      [ "2", "Customer#000000002" ]
                    ]
                  }
                }
                """;

        when(restClient.executeStatement(any())).thenReturn(objectMapper.readTree(json));

        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("SELECT c_custkey, c_name FROM samples.tpch.customer LIMIT 2")
                .catalog("samples")
                .schema("tpch")
                .waitTimeoutSeconds(15)
                .maxRows(2)
                .build();

        SqlExecutionResult result = sqlService.executeGuardedStatement(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getStatementId()).isEqualTo("stmt-777");
        assertThat(result.getColumnNames()).containsExactly("c_custkey", "c_name");
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0)).containsExactly("1", "Customer#000000001");
        verify(restClient).executeStatement(any());
    }

    @Test
    @DisplayName("executeGuardedStatement rejects queries violating cost timeout before network call")
    void testExecuteGuardedStatementRejectsExcessiveTimeout() {
        SqlExecutionRequest request = SqlExecutionRequest.builder()
                .statement("SELECT * FROM samples.tpch.lineitem")
                .waitTimeoutSeconds(120)
                .build();

        assertThatThrownBy(() -> sqlService.executeGuardedStatement(request))
                .isInstanceOf(CostGuardViolationException.class);

        verify(restClient, never()).executeStatement(any());
    }
}
