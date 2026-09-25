package com.rixon.learn.spring.data.databricks.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.ClusterInfo;
import com.rixon.learn.spring.data.databricks.model.SchemaInfo;
import com.rixon.learn.spring.data.databricks.model.TableInfo;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Low-level HTTP client executing Databricks REST API 2.0 / 2.1 operations.
 * Injectable with custom RestClient for testing via MockRestServiceServer or WireMock.
 */
public class DatabricksRestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksRestClient.class);

    private final RestClient restClient;
    private final DatabricksProperties properties;
    private final ObjectMapper objectMapper;

    public DatabricksRestClient(RestClient restClient, DatabricksProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public static DatabricksRestClient create(DatabricksProperties properties, ObjectMapper objectMapper) {
        String baseUrl = properties.resolveHost();
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Databricks-Org-Id", properties.resolveOrgId())
                .defaultRequest(requestHeadersSpec -> {
                    if (properties.hasToken()) {
                        requestHeadersSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken());
                    }
                })
                .build();
        return new DatabricksRestClient(client, properties, objectMapper);
    }

    public WorkspaceUserInfo getCurrentUser() {
        return restClient.get()
                .uri("/api/2.0/preview/scim/v2/Me")
                .retrieve()
                .body(WorkspaceUserInfo.class);
    }

    public List<CatalogInfo> listCatalogs() {
        try {
            String response = restClient.get()
                    .uri("/api/2.1/unity-catalog/catalogs")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode catalogsNode = root.get("catalogs");
            if (catalogsNode == null || !catalogsNode.isArray()) {
                return Collections.emptyList();
            }

            List<CatalogInfo> result = new ArrayList<>();
            for (JsonNode item : catalogsNode) {
                result.add(objectMapper.treeToValue(item, CatalogInfo.class));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to list Unity Catalog catalogs: {}", e.getMessage());
            throw new RuntimeException("Error fetching Unity Catalog catalogs: " + e.getMessage(), e);
        }
    }

    public List<SchemaInfo> listSchemas(String catalogName) {
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/2.1/unity-catalog/schemas")
                            .queryParam("catalog_name", catalogName)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode schemasNode = root.get("schemas");
            if (schemasNode == null || !schemasNode.isArray()) {
                return Collections.emptyList();
            }

            List<SchemaInfo> result = new ArrayList<>();
            for (JsonNode item : schemasNode) {
                result.add(objectMapper.treeToValue(item, SchemaInfo.class));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to list Unity Catalog schemas for catalog '{}': {}", catalogName, e.getMessage());
            throw new RuntimeException("Error fetching schemas for catalog '" + catalogName + "': " + e.getMessage(), e);
        }
    }

    public List<TableInfo> listTables(String catalogName, String schemaName) {
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/2.1/unity-catalog/tables")
                            .queryParam("catalog_name", catalogName)
                            .queryParam("schema_name", schemaName)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode tablesNode = root.get("tables");
            if (tablesNode == null || !tablesNode.isArray()) {
                return Collections.emptyList();
            }

            List<TableInfo> result = new ArrayList<>();
            for (JsonNode item : tablesNode) {
                result.add(objectMapper.treeToValue(item, TableInfo.class));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to list Unity Catalog tables for '{}.{}': {}", catalogName, schemaName, e.getMessage());
            throw new RuntimeException("Error fetching tables for '" + catalogName + "." + schemaName + "': " + e.getMessage(), e);
        }
    }

    public List<ClusterInfo> listClusters() {
        try {
            String response = restClient.get()
                    .uri("/api/2.0/clusters/list")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode clustersNode = root.get("clusters");
            if (clustersNode == null || !clustersNode.isArray()) {
                return Collections.emptyList();
            }

            List<ClusterInfo> result = new ArrayList<>();
            for (JsonNode item : clustersNode) {
                result.add(objectMapper.treeToValue(item, ClusterInfo.class));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to list clusters: {}", e.getMessage());
            throw new RuntimeException("Error fetching clusters from Databricks: " + e.getMessage(), e);
        }
    }

    public ClusterInfo getCluster(String clusterId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/2.0/clusters/get")
                        .queryParam("cluster_id", clusterId)
                        .build())
                .retrieve()
                .body(ClusterInfo.class);
    }

    public String createCluster(Map<String, Object> payload) {
        try {
            String response = restClient.post()
                    .uri("/api/2.0/clusters/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("cluster_id")) {
                return root.get("cluster_id").asText();
            }
            throw new RuntimeException("Databricks did not return a cluster_id: " + response);
        } catch (Exception e) {
            LOGGER.error("Failed to create cluster: {}", e.getMessage());
            throw new RuntimeException("Error creating cluster: " + e.getMessage(), e);
        }
    }

    public void terminateCluster(String clusterId) {
        try {
            restClient.post()
                    .uri("/api/2.0/clusters/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cluster_id", clusterId))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("Successfully requested termination for cluster '{}'", clusterId);
        } catch (Exception e) {
            LOGGER.error("Failed to terminate cluster '{}': {}", clusterId, e.getMessage());
            throw new RuntimeException("Error terminating cluster '" + clusterId + "': " + e.getMessage(), e);
        }
    }

    public JsonNode executeStatement(Map<String, Object> payload) {
        try {
            String response = restClient.post()
                    .uri("/api/2.0/sql/statements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(response);
        } catch (Exception e) {
            LOGGER.error("Failed to execute SQL statement: {}", e.getMessage());
            throw new RuntimeException("Error executing Databricks SQL statement: " + e.getMessage(), e);
        }
    }

    public DatabricksProperties getProperties() {
        return properties;
    }

    public RestClient getUnderlyingRestClient() {
        return restClient;
    }
}
