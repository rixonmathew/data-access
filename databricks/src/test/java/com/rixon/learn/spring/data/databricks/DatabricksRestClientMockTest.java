package com.rixon.learn.spring.data.databricks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.SchemaInfo;
import com.rixon.learn.spring.data.databricks.model.TableInfo;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import com.rixon.learn.spring.data.databricks.service.DatabricksRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DatabricksRestClientMockTest {

    private MockRestServiceServer mockServer;
    private DatabricksRestClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        DatabricksProperties properties = new DatabricksProperties();
        properties.setWorkspaceUrl("https://dbc-06bb552d-57db.cloud.databricks.com/?o=2102279257150258");
        properties.setToken("dapi_test_mock_token_12345");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://dbc-06bb552d-57db.cloud.databricks.com")
                .defaultHeader("Authorization", "Bearer dapi_test_mock_token_12345")
                .defaultHeader("X-Databricks-Org-Id", "2102279257150258");

        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DatabricksRestClient(builder.build(), properties, objectMapper);
    }

    @Test
    @DisplayName("getCurrentUser fetches user profile with Bearer and Org-ID headers")
    void testGetCurrentUser() {
        String json = """
                {
                  "id": "2102279257150258",
                  "userName": "rixon@databricks.com",
                  "displayName": "Rixon Mathew",
                  "active": true
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.0/preview/scim/v2/Me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer dapi_test_mock_token_12345"))
                .andExpect(header("X-Databricks-Org-Id", "2102279257150258"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        WorkspaceUserInfo user = client.getCurrentUser();

        assertThat(user).isNotNull();
        assertThat(user.getUserName()).isEqualTo("rixon@databricks.com");
        assertThat(user.getDisplayName()).isEqualTo("Rixon Mathew");
        assertThat(user.getActive()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("listCatalogs queries Unity Catalog endpoint (0 compute DBUs)")
    void testListCatalogs() {
        String json = """
                {
                  "catalogs": [
                    { "name": "main", "comment": "Main catalog", "catalog_type": "MANAGED_CATALOG" },
                    { "name": "samples", "comment": "Sample datasets", "catalog_type": "SYSTEM_CATALOG" },
                    { "name": "system", "comment": "System telemetry", "catalog_type": "SYSTEM_CATALOG" }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.1/unity-catalog/catalogs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<CatalogInfo> catalogs = client.listCatalogs();

        assertThat(catalogs).hasSize(3);
        assertThat(catalogs.get(1).getName()).isEqualTo("samples");
        assertThat(catalogs.get(1).getCatalogType()).isEqualTo("SYSTEM_CATALOG");
        mockServer.verify();
    }

    @Test
    @DisplayName("listSchemas queries Unity Catalog schemas for catalog")
    void testListSchemas() {
        String json = """
                {
                  "schemas": [
                    { "name": "tpch", "catalog_name": "samples", "comment": "TPC-H benchmarks" },
                    { "name": "nyctaxi", "catalog_name": "samples", "comment": "NYC Taxi trips" }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.1/unity-catalog/schemas?catalog_name=samples"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<SchemaInfo> schemas = client.listSchemas("samples");

        assertThat(schemas).hasSize(2);
        assertThat(schemas.get(0).getName()).isEqualTo("tpch");
        mockServer.verify();
    }

    @Test
    @DisplayName("listTables queries Unity Catalog tables for schema")
    void testListTables() {
        String json = """
                {
                  "tables": [
                    {
                      "name": "customer",
                      "catalog_name": "samples",
                      "schema_name": "tpch",
                      "table_type": "MANAGED",
                      "columns": [
                        { "name": "c_custkey", "type_text": "BIGINT", "nullable": false },
                        { "name": "c_name", "type_text": "STRING", "nullable": true }
                      ]
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.1/unity-catalog/tables?catalog_name=samples&schema_name=tpch"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<TableInfo> tables = client.listTables("samples", "tpch");

        assertThat(tables).hasSize(1);
        TableInfo table = tables.get(0);
        assertThat(table.getName()).isEqualTo("customer");
        assertThat(table.getColumns()).hasSize(2);
        assertThat(table.getColumns().get(0).getName()).isEqualTo("c_custkey");
        mockServer.verify();
    }

    @Test
    @DisplayName("listClusters lists active and stopped clusters")
    void testListClusters() {
        String json = """
                {
                  "clusters": [
                    {
                      "cluster_id": "0925-111111-test",
                      "cluster_name": "Safe-Single-Node",
                      "spark_version": "14.3.x-scala2.12",
                      "node_type_id": "i3.xlarge",
                      "num_workers": 0,
                      "autotermination_minutes": 10,
                      "state": "TERMINATED"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.0/clusters/list"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ClusterInfo> clusters = client.listClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getClusterId()).isEqualTo("0925-111111-test");
        assertThat(clusters.get(0).getState()).isEqualTo("TERMINATED");
        assertThat(clusters.get(0).isSingleNode()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("createCluster posts payload and returns clusterId")
    void testCreateCluster() {
        String responseJson = """
                {
                  "cluster_id": "0925-999999-created"
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.0/clusters/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.cluster_name").value("Safe-Single-Node"))
                .andExpect(jsonPath("$.num_workers").value(0))
                .andExpect(jsonPath("$.autotermination_minutes").value(10))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        String clusterId = client.createCluster(Map.of(
                "cluster_name", "Safe-Single-Node",
                "num_workers", 0,
                "autotermination_minutes", 10
        ));

        assertThat(clusterId).isEqualTo("0925-999999-created");
        mockServer.verify();
    }

    @Test
    @DisplayName("terminateCluster posts delete request with clusterId")
    void testTerminateCluster() {
        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.0/clusters/delete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.cluster_id").value("0925-999999-created"))
                .andRespond(withSuccess());

        client.terminateCluster("0925-999999-created");

        mockServer.verify();
    }

    @Test
    @DisplayName("executeStatement posts SQL query and parses response")
    void testExecuteStatement() {
        String responseJson = """
                {
                  "statement_id": "stmt-001",
                  "status": { "state": "SUCCEEDED" },
                  "manifest": {
                    "schema": {
                      "columns": [
                        { "name": "num", "type_text": "INT" },
                        { "name": "ticker", "type_text": "STRING" }
                      ]
                    },
                    "total_row_count": 1
                  },
                  "result": {
                    "data_array": [
                      [ "1", "AAPL" ]
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo("https://dbc-06bb552d-57db.cloud.databricks.com/api/2.0/sql/statements"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.statement").value("SELECT 1 as num, 'AAPL' as ticker"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        JsonNode response = client.executeStatement(Map.of(
                "statement", "SELECT 1 as num, 'AAPL' as ticker"
        ));

        assertThat(response.get("statement_id").asText()).isEqualTo("stmt-001");
        assertThat(response.get("status").get("state").asText()).isEqualTo("SUCCEEDED");
        mockServer.verify();
    }
}
