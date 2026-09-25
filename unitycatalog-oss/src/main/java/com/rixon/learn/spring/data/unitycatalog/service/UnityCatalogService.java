package com.rixon.learn.spring.data.unitycatalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.unitycatalog.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UnityCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnityCatalogService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UnityCatalogService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public UcCatalog createCatalog(String name, String comment) {
        LOGGER.info("Creating Unity Catalog: {}", name);
        Map<String, Object> request = Map.of(
                "name", name,
                "comment", comment,
                "storage_root", "file:///tmp/uc/" + name
        );
        return restClient.post()
                .uri("/api/2.1/unity-catalog/catalogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UcCatalog.class);
    }

    public UcCatalog getCatalog(String name) {
        return restClient.get()
                .uri("/api/2.1/unity-catalog/catalogs/{name}", name)
                .retrieve()
                .body(UcCatalog.class);
    }

    public List<UcCatalog> listCatalogs() {
        String json = restClient.get()
                .uri("/api/2.1/unity-catalog/catalogs")
                .retrieve()
                .body(String.class);

        List<UcCatalog> catalogs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arrayNode = root.get("catalogs");
            if (arrayNode != null && arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    catalogs.add(objectMapper.treeToValue(node, UcCatalog.class));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse catalogs response: {}", e.getMessage(), e);
        }
        return catalogs;
    }

    public UcSchema createSchema(String catalogName, String schemaName, String comment) {
        LOGGER.info("Creating Unity Catalog schema: {}.{}", catalogName, schemaName);
        Map<String, Object> request = Map.of(
                "name", schemaName,
                "catalog_name", catalogName,
                "comment", comment
        );
        return restClient.post()
                .uri("/api/2.1/unity-catalog/schemas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UcSchema.class);
    }

    public UcSchema getSchema(String fullName) {
        return restClient.get()
                .uri("/api/2.1/unity-catalog/schemas/{fullName}", fullName)
                .retrieve()
                .body(UcSchema.class);
    }

    public UcTable createTable(String catalogName, String schemaName, String tableName, String format, List<UcColumn> columns) {
        LOGGER.info("Registering {} table in Unity Catalog: {}.{}.{}", format, catalogName, schemaName, tableName);
        Map<String, Object> request = new HashMap<>();
        request.put("name", tableName);
        request.put("catalog_name", catalogName);
        request.put("schema_name", schemaName);
        request.put("table_type", "EXTERNAL");
        request.put("data_source_format", format);
        request.put("storage_location", String.format("file:///tmp/uc/%s/%s/%s", catalogName, schemaName, tableName));
        request.put("columns", columns);

        return restClient.post()
                .uri("/api/2.1/unity-catalog/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UcTable.class);
    }

    public UcTable getTable(String fullTableName) {
        return restClient.get()
                .uri("/api/2.1/unity-catalog/tables/{fullTableName}", fullTableName)
                .retrieve()
                .body(UcTable.class);
    }

    public UcVolume createVolume(String catalogName, String schemaName, String volumeName, String volumeType) {
        LOGGER.info("Creating Unity Catalog volume: {}.{}.{}", catalogName, schemaName, volumeName);
        Map<String, Object> request = Map.of(
                "name", volumeName,
                "catalog_name", catalogName,
                "schema_name", schemaName,
                "volume_type", "EXTERNAL",
                "storage_location", String.format("file:///tmp/uc/%s/%s/volumes/%s", catalogName, schemaName, volumeName)
        );
        return restClient.post()
                .uri("/api/2.1/unity-catalog/volumes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UcVolume.class);
    }

    public void deleteCatalog(String name, boolean force) {
        restClient.delete()
                .uri("/api/2.1/unity-catalog/catalogs/{name}?force={force}", name, force)
                .retrieve()
                .toBodilessEntity();
        LOGGER.info("Deleted Unity Catalog: {}", name);
    }
}
